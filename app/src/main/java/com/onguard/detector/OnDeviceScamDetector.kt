package com.onguard.detector

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.onguard.domain.model.DetectionMethod
import com.onguard.domain.model.ScamAnalysis
import com.onguard.domain.model.ScamType
import com.onguard.util.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * 온디바이스 Gemma(MediaPipe)를 사용하는 스캠 분석기.
 *
 * - [ScamLlmClient] 구현. 화이트리스트 매칭 시에만 DI에서 주입됨.
 * - .task 모델이 assets 또는 filesDir에 있을 때만 [isAvailable] true.
 */
@Singleton
class OnDeviceScamDetector @Inject constructor(
    @ApplicationContext private val context: Context
) : ScamLlmClient {

    companion object {
        private const val TAG = "OnDeviceGemma"
        private const val ASSET_MODEL_PATH = "models/gemma.task"
        private const val MODEL_FILE_NAME = "gemma_llm.task"
        private const val MAX_TOKENS = 512
        private const val MAX_TOP_K = 40
    }

    private var llmInference: LlmInference? = null
    private var modelPathResolved: String? = null

    override fun isAvailable(): Boolean {
        if (modelPathResolved != null) {
            return File(modelPathResolved!!).exists()
        }
        return copyModelFromAssetsIfNeeded() != null
    }

    /**
     * assets의 .task 모델을 filesDir로 복사하고 경로 반환. 없으면 null.
     */
    private fun copyModelFromAssetsIfNeeded(): String? {
        val outFile = File(context.filesDir, MODEL_FILE_NAME)
        if (outFile.exists()) {
            modelPathResolved = outFile.absolutePath
            return modelPathResolved
        }
        return try {
            context.assets.open(ASSET_MODEL_PATH).use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            modelPathResolved = outFile.absolutePath
            DebugLog.debugLog(TAG) { "step=model_copied path=$modelPathResolved" }
            modelPathResolved
        } catch (e: Exception) {
            DebugLog.debugLog(TAG) { "step=model_not_found asset=$ASSET_MODEL_PATH reason=${e.message}" }
            null
        }
    }

    private fun getOrCreateLlmInference(): LlmInference? {
        if (llmInference != null) return llmInference
        val path = copyModelFromAssetsIfNeeded() ?: return null
        return try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(path)
                .setMaxTokens(MAX_TOKENS)
                .setMaxTopK(MAX_TOP_K)
                .build()
            LlmInference.createFromOptions(context, options).also {
                llmInference = it
            }
        } catch (e: Exception) {
            DebugLog.warnLog(TAG) { "step=llm_init_failed reason=${e.message}" }
            null
        }
    }

    override suspend fun analyze(request: ScamLlmRequest): ScamAnalysis? = withContext(Dispatchers.IO) {
        val inference = getOrCreateLlmInference() ?: return@withContext null
        val prompt = buildPrompt(
            recentContextLines = request.recentContext.lines().filter { it.isNotBlank() },
            currentMessage = request.currentMessage,
            ruleReasons = request.ruleReasons,
            detectedKeywords = request.detectedKeywords
        )
        try {
            val responseText = inference.generateResponse(prompt)
            parseResponse(responseText, request.originalText, request.recentContext)
        } catch (e: Exception) {
            DebugLog.warnLog(TAG) { "step=analyze_failed reason=${e.message}" }
            null
        }
    }

    private fun buildPrompt(
        recentContextLines: List<String>,
        currentMessage: String,
        ruleReasons: List<String>,
        detectedKeywords: List<String>
    ): String {
        val reasonsText = ruleReasons.joinToString("; ").ifEmpty { "없음" }
        val keywordsText = detectedKeywords.joinToString(", ").ifEmpty { "없음" }
        val recentBlock = if (recentContextLines.isEmpty()) "- (최근 대화 없음)"
        else recentContextLines.joinToString("\n") { "- $it" }
        return """
            시스템: 당신은 사용자에게 스캠(사기) 위험을 알리는 도우미입니다. 메시지와 룰 기반 탐지 정보를 바탕으로 사기 가능 여부를 판단하고, 사용자가 위험을 쉽게 인식할 수 있도록 간단하고 명확하게 안내합니다.

            [최근 대화]
            $recentBlock

            [현재 메시지]
            $currentMessage

            추가 정보:
            - 룰 기반 탐지 이유: $reasonsText
            - 탐지된 키워드: $keywordsText

            요청:
            아래 JSON 형식으로 응답하세요. warningMessage는 반드시 2~3문장으로, 왜 이 메시지를 스캠으로 의심했는지(예: 금전 요구, 긴급성, 의심 URL, 사칭 등)를 사용자에게 쉽게 설명하세요. 다른 텍스트는 포함하지 말고 JSON만 출력하세요.

            JSON 필드 설명:
            - confidence: 사기 가능성 (0~100 정수, 50 이상이면 사기 의심)
            - scamType: 사기 유형 (INVESTMENT/USED_TRADE/PHISHING/VOICE_PHISHING/IMPERSONATION/LOAN/UNKNOWN 중 하나)
            - warningMessage: 사용자에게 보여줄 경고 문구. 2~3문장, 한국어. 왜 스캠 가능성을 봤는지 간단히 설명한 뒤, 행동 권고(의심 링크 클릭 금지 등)를 한 문장으로.
            - reasons: 탐지 이유 목록 (짧고 명확하게)
            - suspiciousParts: 원문에서 의심되는 표현 인용 (최대 3개)

            출력 형식:
            ```json
            {
              "confidence": 75,
              "scamType": "PHISHING",
              "warningMessage": "금전 요구와 긴급성 표현이 함께 있어 사기 가능성이 있습니다. 의심스러운 링크가 포함된 메시지입니다. 알 수 없는 링크는 클릭하지 마세요.",
              "reasons": ["피싱 URL 감지", "긴급 유도 표현"],
              "suspiciousParts": ["지금 바로 클릭", "bit.ly/xxx"]
            }
            ```
        """.trimIndent()
    }

    private fun parseResponse(
        response: String,
        originalText: String,
        recentContext: String
    ): ScamAnalysis? {
        val jsonString = extractJsonFromResponse(response) ?: return null
        return try {
            val json = JSONObject(jsonString)
            parseJsonToScamAnalysis(json, originalText, recentContext)
        } catch (e: Exception) {
            DebugLog.warnLog(TAG) { "step=parse_failed reason=${e.message}" }
            null
        }
    }

    private fun extractJsonFromResponse(response: String): String? {
        val codeBlockRegex = Regex("""```json\s*\n?([\s\S]*?)\n?```""", RegexOption.IGNORE_CASE)
        codeBlockRegex.find(response)?.let { return it.groupValues[1].trim() }
        val jsonRegex = Regex("""\{[\s\S]*\}""")
        jsonRegex.find(response)?.let { return it.value.trim() }
        return null
    }

    private fun parseJsonToScamAnalysis(
        json: JSONObject,
        originalText: String,
        recentContext: String
    ): ScamAnalysis {
        val confidenceInt = json.optInt("confidence", 50)
        val confidence = (confidenceInt / 100f).coerceIn(0f, 1f)
        val scamTypeStr = json.optString("scamType", "UNKNOWN")
        val scamType = try {
            ScamType.valueOf(scamTypeStr)
        } catch (e: Exception) {
            inferScamType("${recentContext.ifBlank { originalText }}")
        }
        val warningMessage = json.optString("warningMessage", "").ifBlank {
            defaultWarning(scamType, confidence)
        }
        val reasons = mutableListOf<String>()
        json.optJSONArray("reasons")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optString(i)?.takeIf { it.isNotBlank() }?.let { reasons.add(it) }
            }
        }
        if (reasons.isEmpty()) reasons.add("온디바이스 LLM 분석 결과")
        val suspiciousParts = mutableListOf<String>()
        json.optJSONArray("suspiciousParts")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optString(i)?.takeIf { it.isNotBlank() }?.let { suspiciousParts.add(it) }
            }
        }
        return ScamAnalysis(
            isScam = confidence >= 0.5f,
            confidence = confidence,
            reasons = reasons,
            detectedKeywords = emptyList(),
            detectionMethod = DetectionMethod.LLM,
            scamType = scamType,
            warningMessage = warningMessage,
            suspiciousParts = suspiciousParts
        )
    }

    private fun defaultWarning(scamType: ScamType, confidence: Float): String {
        val percent = (confidence * 100).toInt()
        return when (scamType) {
            ScamType.INVESTMENT -> "투자 사기가 의심됩니다 (위험도 $percent%)."
            ScamType.USED_TRADE -> "중고거래 사기가 의심됩니다 (위험도 $percent%)."
            ScamType.PHISHING -> "피싱 링크가 포함되어 있습니다 (위험도 $percent%)."
            ScamType.VOICE_PHISHING -> "보이스피싱/스미싱 의심 (위험도 $percent%)."
            ScamType.IMPERSONATION -> "사칭 사기가 의심됩니다 (위험도 $percent%)."
            ScamType.LOAN -> "대출 사기가 의심됩니다 (위험도 $percent%)."
            else -> "사기 의심 메시지입니다 (위험도 $percent%)."
        }
    }

    private fun inferScamType(text: String): ScamType = when {
        text.contains("투자") || text.contains("수익") || text.contains("코인") || text.contains("주식") -> ScamType.INVESTMENT
        text.contains("입금") || text.contains("선결제") || text.contains("거래") || text.contains("택배") -> ScamType.USED_TRADE
        text.contains("URL") || text.contains("링크") || text.contains("피싱") -> ScamType.PHISHING
        text.contains("사칭") || text.contains("기관") -> ScamType.IMPERSONATION
        text.contains("대출") -> ScamType.LOAN
        else -> ScamType.UNKNOWN
    }
}
