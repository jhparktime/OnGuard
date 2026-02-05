package com.onguard.detector

import android.util.Log
import com.onguard.domain.model.DetectionMethod
import com.onguard.domain.model.ScamAnalysis
import com.onguard.domain.model.ScamType
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * 하이브리드 스캠 탐지기.
 *
 * Rule-based([KeywordMatcher], [UrlAnalyzer])와 LLM([LLMScamAnalyzer]) 탐지를 결합하여
 * 정확도 높은 스캠 탐지를 수행한다.
 *
 * ## 탐지 흐름
 * 1. Rule-based 1차 필터 (키워드 + URL)
 * 2. 신뢰도 0.3~0.7 구간이면 LLM 추가 분석
 * 3. 가중 평균(Rule 40%, LLM 60%)으로 최종 판정
 *
 * @param keywordMatcher 키워드 기반 규칙 탐지기
 * @param urlAnalyzer URL 위험도 분석기
 * @param llmScamAnalyzer LLM 기반 탐지기 (테스트 시 mock 주입 가능)
 * @param config 임계값·가중치 (테스트 시 다른 값 주입 가능)
 */
@Singleton
class HybridScamDetector @Inject constructor(
    private val keywordMatcher: KeywordMatcher,
    private val urlAnalyzer: UrlAnalyzer,
    private val llmScamAnalyzer: LLMScamAnalyzer,
    private val config: HybridScamDetectorConfig = HybridScamDetectorConfig.Default
) {

    companion object {
        private const val TAG = "HybridScamDetector"
    }

    /**
     * LLM 모델 초기화
     * Application 시작 시 호출 권장
     */
    suspend fun initializeLLM(): Boolean {
        return llmScamAnalyzer.initialize()
    }

    /**
     * LLM 사용 가능 여부
     */
    fun isLLMAvailable(): Boolean = llmScamAnalyzer.isAvailable()

    /**
     * 주어진 텍스트를 분석하여 스캠 여부와 상세 결과를 반환한다.
     *
     * @param text 분석할 채팅 메시지
     * @param useLLM true이면 애매한 구간에서 LLM 분석 시도, false이면 Rule-based만 사용
     * @return [ScamAnalysis] 최종 분석 결과 (스캠 여부, 신뢰도, 이유, 경고 메시지 등)
     */
    suspend fun analyze(text: String, useLLM: Boolean = true): ScamAnalysis {
        // 1. Rule-based keyword detection (fast)
        val keywordResult = keywordMatcher.analyze(text)

        // 2. URL analysis
        val urlResult = urlAnalyzer.analyze(text)

        // 3. Combine rule-based results
        val combinedReasons = mutableListOf<String>()
        combinedReasons.addAll(keywordResult.reasons)
        combinedReasons.addAll(urlResult.reasons)

        // 4. Calculate rule-based confidence
        var ruleConfidence = keywordResult.confidence

        // URL 분석 결과 반영
        // - 의심 URL이 있으면 최소한 URL 위험도만큼 신뢰도 보장
        // - 추가로 URL 위험도의 30%를 보너스로 부여
        // - 이유: URL이 포함된 스캠은 위험도가 높음 (피싱 링크 가능성)
        if (urlResult.suspiciousUrls.isNotEmpty()) {
            ruleConfidence = max(ruleConfidence, urlResult.riskScore)
            ruleConfidence += urlResult.riskScore * 0.3f
        }
        ruleConfidence = ruleConfidence.coerceIn(0f, 1f)

        // 5. Early return for very high confidence (명확한 스캠)
        if (ruleConfidence > config.highConfidenceThreshold) {
            Log.d(TAG, "High confidence rule-based detection: $ruleConfidence")
            return createRuleBasedResult(
                ruleConfidence,
                combinedReasons,
                keywordResult.detectedKeywords,
                urlResult.suspiciousUrls.isNotEmpty()
            )
        }

        // 6. Additional combination checks for medium confidence
        if (ruleConfidence > config.mediumConfidenceThreshold) {
            val hasUrgency = text.contains("긴급", ignoreCase = true) ||
                    text.contains("급하", ignoreCase = true) ||
                    text.contains("빨리", ignoreCase = true)

            val hasMoney = text.contains("입금", ignoreCase = true) ||
                    text.contains("송금", ignoreCase = true) ||
                    text.contains("계좌", ignoreCase = true)

            // 스캠 황금 패턴: 긴급성 + 금전 요구 + URL
            // - 전형적인 피싱 패턴으로 추가 15% 보너스
            // - 예: "급하게 이 링크로 입금해주세요"
            if (hasUrgency && hasMoney && urlResult.urls.isNotEmpty()) {
                ruleConfidence += 0.15f
                combinedReasons.add("의심스러운 조합: 긴급 + 금전 + URL")
            }
        }

        // 7. LLM 분석 (애매한 경우에만, 그리고 그 시점에 지연 초기화 시도)
        if (useLLM && ruleConfidence in config.llmTriggerLow..config.llmTriggerHigh) {
            Log.d(TAG, "LLM candidate range, confidence=$ruleConfidence (will try lazy init if needed)")

            // 아직 초기화 안 되어 있으면, 이 시점에서 한 번만 초기화 시도
            if (!llmScamAnalyzer.isAvailable()) {
                Log.d(TAG, "LLM not initialized yet. Trying lazy initialization...")
                try {
                    val initSuccess = llmScamAnalyzer.initialize()
                    Log.d(
                        TAG,
                        "Lazy LLM initialization result: success=$initSuccess, available=${llmScamAnalyzer.isAvailable()}"
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error during lazy LLM initialization", e)
                }
            }

            // 초기화 이후에도 사용 불가하면 LLM 분석은 건너뛴다
            if (!llmScamAnalyzer.isAvailable()) {
                Log.w(TAG, "LLM still not available after lazy init. Falling back to rule-based result.")
            } else {
                Log.d(TAG, "Triggering LLM analysis for confidence: $ruleConfidence")

                val llmContext = LLMScamDetector.LlmContext(
                    ruleConfidence = ruleConfidence,
                    ruleReasons = combinedReasons,
                    detectedKeywords = keywordResult.detectedKeywords,
                    urls = urlResult.urls,
                    suspiciousUrls = urlResult.suspiciousUrls,
                    urlReasons = urlResult.reasons
                )

                val llmResult = llmScamAnalyzer.analyze(text, llmContext)

                Log.d(
                    TAG,
                    "LLM result: " +
                        if (llmResult == null) "null" else
                            "isScam=${llmResult.isScam}, " +
                            "confidence=${llmResult.confidence}, " +
                            "scamType=${llmResult.scamType}, " +
                            "reasons=${llmResult.reasons.joinToString(limit = 3)}"
                )

                if (llmResult != null) {
                    return combineResults(
                        ruleConfidence = ruleConfidence,
                        ruleReasons = combinedReasons,
                        detectedKeywords = keywordResult.detectedKeywords,
                        llmResult = llmResult,
                        config = config
                    )
                }
            }
        }

        // 8. Final rule-based result
        return createRuleBasedResult(
            ruleConfidence.coerceIn(0f, 1f),
            combinedReasons,
            keywordResult.detectedKeywords,
            urlResult.suspiciousUrls.isNotEmpty()
        )
    }

    /**
     * Rule-based 결과와 LLM 결과를 가중 평균으로 결합한다.
     *
     * @param ruleConfidence 규칙 기반 신뢰도
     * @param ruleReasons 규칙 기반 탐지 사유
     * @param detectedKeywords 탐지된 키워드 목록
     * @param llmResult LLM 분석 결과
     * @return 결합된 [ScamAnalysis]
     */
    private fun combineResults(
        ruleConfidence: Float,
        ruleReasons: List<String>,
        detectedKeywords: List<String>,
        llmResult: ScamAnalysis,
        config: HybridScamDetectorConfig
    ): ScamAnalysis {
        // 가중 평균으로 최종 신뢰도 계산
        val combinedConfidence = (ruleConfidence * config.ruleWeight + llmResult.confidence * config.llmWeight)
            .coerceIn(0f, 1f)

        // 이유 목록 결합 (중복 제거)
        val allReasons = (ruleReasons + llmResult.reasons).distinct()

        Log.d(TAG, "Combined result - Rule: $ruleConfidence, LLM: ${llmResult.confidence}, Final: $combinedConfidence")

        return ScamAnalysis(
            isScam = combinedConfidence > config.finalScamThreshold || llmResult.isScam,
            confidence = combinedConfidence,
            reasons = allReasons,
            detectedKeywords = detectedKeywords,
            detectionMethod = DetectionMethod.HYBRID,
            scamType = llmResult.scamType,
            warningMessage = llmResult.warningMessage,
            suspiciousParts = llmResult.suspiciousParts
        )
    }

    /**
     * Rule-based만으로 [ScamAnalysis]를 생성한다.
     *
     * @param confidence 신뢰도
     * @param reasons 탐지 사유 목록
     * @param detectedKeywords 탐지된 키워드
     * @param hasUrlIssues URL 이상 여부 (HYBRID vs RULE_BASED 구분용)
     * @return [ScamAnalysis]
     */
    private fun createRuleBasedResult(
        confidence: Float,
        reasons: List<String>,
        detectedKeywords: List<String>,
        hasUrlIssues: Boolean
    ): ScamAnalysis {
        val scamType = ScamTypeInferrer.inferScamType(reasons)
        val warningMessage = RuleBasedWarningGenerator.generateWarning(scamType, confidence)

        return ScamAnalysis(
            isScam = confidence > config.finalScamThreshold,
            confidence = confidence,
            reasons = reasons,
            detectedKeywords = detectedKeywords,
            detectionMethod = if (hasUrlIssues) DetectionMethod.HYBRID else DetectionMethod.RULE_BASED,
            scamType = scamType,
            warningMessage = warningMessage,
            suspiciousParts = detectedKeywords.take(3)
        )
    }

    fun close() {
        llmScamAnalyzer.close()
    }
}
