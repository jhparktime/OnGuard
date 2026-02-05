package com.onguard.llm

import android.content.Context
import android.util.Log
import com.onguard.llm.stub.OfflineLlm
import com.onguard.llm.stub.OfflineLlmConfig
import com.onguard.util.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class SherpaPhishingAnalyzer(private val context: Context) {

    private var llm: OfflineLlm? = null
    // 모델 파일은 app/src/main/assets/models/model_q4f16.onnx 에 위치
    private val modelAssetPath = "models/model_q4f16.onnx"
    // 토크나이저는 Hugging Face 스타일 구성 중 tokenizer.json 을 사용
    private val tokenizerAssetPath = "tokenizers/smollm2/tokenizer.json"

    // 1. 모델 초기화 (앱 시작 시 호출)
    suspend fun initModel(): Boolean = withContext(Dispatchers.IO) {
        try {
            DebugLog.debugLog("OnGuardSherpa") { "step=init start modelAsset=$modelAssetPath tokenizerAsset=$tokenizerAssetPath" }
            val modelPath = copyAssetToFiles(modelAssetPath)
            val tokenPath = copyAssetToFiles(tokenizerAssetPath)

            DebugLog.debugLog("OnGuardSherpa") { "step=init copied modelPath=$modelPath tokenizerPath=$tokenPath" }
            val config = OfflineLlmConfig.builder()
                .setModel(modelPath)
                .setTokenizer(tokenPath)
                .build()

            llm = OfflineLlm(config)
            DebugLog.debugLog("OnGuardSherpa") { "step=init success" }
            true
        } catch (e: Exception) {
            Log.e("OnGuardSherpa", "모델 로딩 실패: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    // 2. 피싱 분석 실행
    suspend fun analyze(text: String): String = withContext(Dispatchers.IO) {
        if (llm == null) return@withContext "모델이 준비되지 않았습니다."

        DebugLog.debugLog("OnGuardSherpa") {
            val masked = DebugLog.maskText(text, maxLen = 30)
            "step=analyze input length=${text.length} masked=\"$masked\""
        }

        // SmolLM2 프롬프트 포맷 (ChatML)
        val prompt = """
            <|im_start|>system
            You are a security expert named 'On-Guard'. Analyze the following text for phishing risks.
            Answer in Korean. Format: [위험도: 높음/중간/낮음] 이유 한 줄 요약.
            <|im_end|>
            <|im_start|>user
            $text
            <|im_end|>
            <|im_start|>assistant
        """.trimIndent()

        return@withContext try {
            val start = System.currentTimeMillis()
            val result = llm?.generate(prompt) ?: "분석 오류"
            val elapsed = System.currentTimeMillis() - start

            val firstLine = result.lineSequence().firstOrNull()?.take(120) ?: ""
            DebugLog.debugLog("OnGuardSherpa") {
                "step=analyze done elapsedMs=$elapsed firstLine=\"$firstLine\""
            }
            result
        } catch (e: Exception) {
            Log.e("OnGuardSherpa", "추론 중 에러: ${e.message}")
            "분석 중 오류 발생"
        }
    }

    // 3. 자원 해제
    fun close() {
        llm?.release()
        llm = null
    }

    // [헬퍼 함수] Assets -> 내부 저장소 복사
    private fun copyAssetToFiles(assetPath: String): String {
        val fileName = assetPath.substringAfterLast('/')
        val target = File(context.filesDir, fileName)
        if (!target.exists() || target.length() == 0L) {
            // ensure parent exists (for future changes)
            target.parentFile?.mkdirs()
            context.assets.open(assetPath).use { inputStream ->
                FileOutputStream(target).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
        return target.absolutePath
    }
}
