package com.onguard.detector

import android.content.Context
import android.util.Log
import com.onguard.domain.model.DetectionMethod
import com.onguard.domain.model.ScamAnalysis
import com.onguard.domain.model.ScamType
import com.onguard.llm.LlamaManager
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LLM 기반 스캠 탐지기.
 *
 * llama.cpp + Qwen([LlamaManager]) GGUF 모델로 채팅 메시지를 분석하여
 * 스캠 여부·신뢰도·경고 메시지·의심 문구를 생성한다.
 * 모델 파일이 없거나 초기화 실패 시 [analyze]는 null을 반환하며, Rule-based만 사용된다.
 *
 * @param context [ApplicationContext] 앱 컨텍스트 (assets/filesDir 접근용)
 * @param llamaManager llama.cpp + Qwen GGUF 모델 매니저
 */
@Singleton
class LLMScamDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val llamaManager: LlamaManager
) {
    companion object {
        private const val TAG = "LLMScamDetector"
        /** LLM 입력 길이 상한 (문자 수 기준, 토큰/메모리 절감) */
        private const val MAX_INPUT_CHARS = 1500
    }

    /**
     * Rule 기반·URL 분석 결과 등, LLM에 전달할 추가 컨텍스트.
     *
     * DB(KISA 피싱 URL 등)를 이미 활용한 UrlAnalyzer/KeywordMatcher의 결과를
     * LLM 프롬프트에 함께 담기 위해 사용한다.
     */
    data class LlmContext(
        /** 규칙 기반 신뢰도 (0.0~1.0) */
        val ruleConfidence: Float? = null,
        /** 규칙 기반 탐지 사유 목록 */
        val ruleReasons: List<String> = emptyList(),
        /** 규칙 기반에서 탐지된 키워드 목록 */
        val detectedKeywords: List<String> = emptyList(),
        /** 텍스트에서 추출된 전체 URL 목록 */
        val urls: List<String> = emptyList(),
        /** UrlAnalyzer 기준으로 위험하다고 간주된 URL 목록 */
        val suspiciousUrls: List<String> = emptyList(),
        /** URL 관련 위험 사유 (KISA DB 매치, 무료 도메인 등) */
        val urlReasons: List<String> = emptyList()
    )

    private val gson = Gson()
    private var isInitialized = false
    private var initializationAttempted = false

    /**
     * LLM 모델을 초기화한다.
     *
     * LlamaManager(llama.cpp + Qwen GGUF)를 로드한다.
     * 이미 초기화된 경우 즉시 true를 반환한다.
     *
     * @return 성공 시 true, 모델 없음/예외 시 false
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "=== LLM Initialization Started (llama.cpp + Qwen) ===")

        if (isInitialized) {
            Log.d(TAG, "LLM already initialized, skipping")
            return@withContext true
        }

        try {
            val ok = llamaManager.initModel()
            if (ok) {
                isInitialized = true
                initializationAttempted = true
                Log.i(TAG, "=== LLM backend: llama.cpp + Qwen (LlamaManager) ===")
                return@withContext true
            }
        } catch (e: Throwable) {
            Log.w(TAG, "LlamaManager init failed: ${e.message}", e)
        }
        false
    }

    /**
     * LLM 모델 사용 가능 여부를 반환한다.
     *
     * @return 초기화 완료 및 인스턴스 존재 시 true
     */
    fun isAvailable(): Boolean = isInitialized

    /**
     * 주어진 텍스트를 LLM으로 분석하여 스캠 여부와 상세 결과를 반환한다.
     *
     * @param text 분석할 채팅 메시지
     * @param context Rule/URL 기반 1차 분석 결과 등 추가 컨텍스트 (선택)
     * @return [ScamAnalysis] 분석 결과. 모델 미사용/빈 응답/파싱 실패 시 null
     */
    suspend fun analyze(text: String, llmContext: LlmContext? = null): ScamAnalysis? = withContext(Dispatchers.Default) {
        val input = if (text.length > MAX_INPUT_CHARS) {
            Log.d(TAG, "Input truncated for LLM: ${text.length} -> $MAX_INPUT_CHARS chars")
            text.take(MAX_INPUT_CHARS)
        } else text
        Log.d(TAG, "=== LLM Analysis Started ===")
        Log.d(TAG, "  - Text length: ${input.length} chars")
        Log.d(TAG, "  - Context provided: ${llmContext != null}")

        if (!isAvailable()) {
            Log.w(TAG, "LLM not available (isInitialized=$isInitialized), skipping analysis")
            return@withContext null
        }

        try {
            val userInput = buildLlamaUserInput(input, llmContext)
            Log.d(TAG, "LlamaManager.analyzeText() (length=${userInput.length})")
            val response = llamaManager.analyzeText(userInput)
            if (response.isBlank() || response == "분석 실패") {
                Log.w(TAG, "LlamaManager returned empty or fallback")
                return@withContext null
            }
            val result = parseResponse(response)
            if (result != null) {
                Log.d(TAG, "=== LLM Analysis Success (llama.cpp + Qwen) ===")
                Log.d(TAG, "  - isScam: ${result.isScam}, confidence: ${result.confidence}")
            }
            return@withContext result
        } catch (e: Exception) {
            Log.e(TAG, "LlamaManager.analyzeText() error", e)
            return@withContext null
        }
    }

    /**
     * LlamaManager(Qwen)에 넘길 사용자 입력을 만든다.
     * Rule/URL 컨텍스트 + [메시지] 형태로, Qwen이 JSON으로 답하도록 시스템 프롬프트에서 지시함.
     */
    private fun buildLlamaUserInput(text: String, llmContext: LlmContext?): String {
        val contextBlock = buildContextBlock(llmContext)
        return if (contextBlock.isNotBlank()) {
            "$contextBlock\n\n[메시지]\n$text"
        } else {
            "[메시지]\n$text"
        }
    }

    /** Rule/URL 1차 분석 요약 문자열 (buildLlamaUserInput용) */
    private fun buildContextBlock(llmContext: LlmContext?): String {
        val maxListItems = 8
        return buildString {
            if (llmContext == null) return@buildString

            if (llmContext.ruleConfidence != null || llmContext.ruleReasons.isNotEmpty() || llmContext.detectedKeywords.isNotEmpty()) {
                appendLine("[Rule-based 1차 분석 요약]")
                llmContext.ruleConfidence?.let { appendLine("- rule_confidence: $it") }
                if (llmContext.detectedKeywords.isNotEmpty()) {
                    appendLine("- detected_keywords: ${llmContext.detectedKeywords.take(maxListItems).joinToString()}")
                }
                if (llmContext.ruleReasons.isNotEmpty()) {
                    appendLine("- rule_reasons:")
                    llmContext.ruleReasons.take(maxListItems).forEach { appendLine("  - $it") }
                }
                appendLine()
            }
            if (llmContext.urls.isNotEmpty() || llmContext.suspiciousUrls.isNotEmpty() || llmContext.urlReasons.isNotEmpty()) {
                appendLine("[URL/DB 기반 분석 요약]")
                if (llmContext.urls.isNotEmpty()) appendLine("- urls: ${llmContext.urls.take(maxListItems).joinToString()}")
                if (llmContext.suspiciousUrls.isNotEmpty()) appendLine("- suspicious_urls: ${llmContext.suspiciousUrls.take(maxListItems).joinToString()}")
                if (llmContext.urlReasons.isNotEmpty()) {
                    appendLine("- url_reasons:")
                    llmContext.urlReasons.take(maxListItems).forEach { appendLine("  - $it") }
                }
            }
        }.trimEnd()
    }

    /**
     * LLM 응답 문자열에서 JSON 블록을 추출해 [ScamAnalysis]로 파싱한다.
     *
     * @param response LLM 원시 응답 (앞뒤 일반 텍스트 허용)
     * @return 파싱 성공 시 [ScamAnalysis], 실패 시 null
     */
    private fun parseResponse(response: String): ScamAnalysis? {
        Log.d(TAG, "Parsing LLM response...")
        return try {
            // JSON 부분만 추출 (LLM이 추가 텍스트를 생성할 수 있음)
            val jsonStart = response.indexOf('{')
            val jsonEnd = response.lastIndexOf('}')

            Log.d(TAG, "  - JSON start index: $jsonStart")
            Log.d(TAG, "  - JSON end index: $jsonEnd")

            if (jsonStart == -1 || jsonEnd == -1 || jsonEnd <= jsonStart) {
                Log.w(TAG, "No valid JSON found in response")
                Log.w(TAG, "  - Response length: ${response.length}")
                Log.w(TAG, "  - Response preview: ${response.take(500)}")
                return null
            }

            val jsonString = response.substring(jsonStart, jsonEnd + 1)
            Log.d(TAG, "  - Extracted JSON length: ${jsonString.length}")
            Log.v(TAG, "  - JSON content: $jsonString")

            val llmResult = gson.fromJson(jsonString, LLMResponse::class.java)
            Log.d(TAG, "  - Parsed LLM result: isScam=${llmResult.isScam}, confidence=${llmResult.confidence}")

            ScamAnalysis(
                isScam = llmResult.isScam,
                confidence = llmResult.confidence.coerceIn(0f, 1f),
                reasons = llmResult.reasons.orEmpty(),
                detectedKeywords = emptyList(),
                detectionMethod = DetectionMethod.LLM,
                scamType = parseScamType(llmResult.scamType),
                warningMessage = llmResult.warningMessage,
                suspiciousParts = llmResult.suspiciousParts.orEmpty()
            )
        } catch (e: Exception) {
            Log.e(TAG, "=== Failed to parse LLM response ===", e)
            Log.e(TAG, "  - Exception type: ${e.javaClass.name}")
            Log.e(TAG, "  - Exception message: ${e.message}")
            Log.e(TAG, "  - Response length: ${response.length}")
            Log.e(TAG, "  - Response preview: ${response.take(500)}")
            e.printStackTrace()
            null
        }
    }

    /**
     * LLM이 반환한 스캠 유형 문자열을 [ScamType] enum으로 변환한다.
     *
     * @param typeString "투자사기", "중고거래사기", "정상" 등 한글 문자열
     * @return 대응되는 [ScamType]
     */
    private fun parseScamType(typeString: String): ScamType {
        return when {
            typeString.contains("투자") -> ScamType.INVESTMENT
            typeString.contains("중고") || typeString.contains("거래") -> ScamType.USED_TRADE
            typeString.contains("피싱") -> ScamType.PHISHING
            typeString.contains("사칭") -> ScamType.IMPERSONATION
            typeString.contains("로맨스") -> ScamType.ROMANCE
            typeString.contains("대출") -> ScamType.LOAN
            typeString.contains("정상") -> ScamType.SAFE
            else -> ScamType.UNKNOWN
        }
    }

    /**
     * LLM 인스턴스를 해제하고 리소스를 반환한다.
     *
     * 앱 종료 또는 탐지기 교체 시 호출 권장.
     */
    fun close() {
        try {
            llamaManager.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error while closing LlamaManager", e)
        }
        isInitialized = false
        initializationAttempted = false
    }

    /**
     * LLM 응답 JSON 파싱용 내부 DTO.
     *
     * @property isScam 스캠 여부
     * @property confidence 신뢰도 0~1
     * @property scamType 한글 유형 문자열
     * @property warningMessage 사용자용 경고 문구
     * @property reasons 위험 요소 목록
     * @property suspiciousParts 의심 문구 인용 목록
     */
    private data class LLMResponse(
        @SerializedName("isScam")
        val isScam: Boolean = false,

        @SerializedName("confidence")
        val confidence: Float = 0f,

        @SerializedName("scamType")
        val scamType: String = "정상",

        @SerializedName("warningMessage")
        val warningMessage: String = "",

        @SerializedName("reasons")
        val reasons: List<String>? = null,

        @SerializedName("suspiciousParts")
        val suspiciousParts: List<String>? = null
    )
}