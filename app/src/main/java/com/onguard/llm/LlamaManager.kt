package com.onguard.llm

import android.content.Context
import android.util.Log
import de.kherud.llama.InferenceParameters
import de.kherud.llama.LlamaModel
import de.kherud.llama.ModelParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * llama.cpp(java-llama.cpp) 기반 온디바이스 LLM 매니저.
 *
 * - [LlamaConfig] 경로로 assets → filesDir GGUF 복사 후 로드
 * - [QwenPromptBuilder]로 ChatML 프롬프트 구성
 * - 코루틴(Dispatchers.IO)에서 비동기 초기화/추론
 */
class LlamaManager(private val context: Context) : LlamaBackend {

    companion object {
        private const val TAG = "LlamaManager"
    }

    private var llamaModel: LlamaModel? = null

    /**
     * 모델 파일을 assets → filesDir 로 복사한 뒤 LlamaModel 초기화.
     *
     * @return [LlamaInitResult.Success] 또는 [LlamaInitResult.Failure]
     */
    override suspend fun initModel(): LlamaInitResult = withContext(Dispatchers.IO) {
        if (llamaModel != null) {
            Log.d(TAG, "Llama model already initialized.")
            return@withContext LlamaInitResult.Success
        }

        try {
            val modelsDir = File(context.filesDir, LlamaConfig.LOCAL_MODEL_DIR).apply {
                if (!exists()) mkdirs()
            }
            val localModelFile = File(modelsDir, LlamaConfig.LOCAL_MODEL_NAME)

            if (!localModelFile.exists()) {
                Log.d(TAG, "Copying GGUF model from assets to filesDir...")
                context.assets.open(LlamaConfig.ASSET_MODEL_PATH).use { input ->
                    localModelFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Model copied to: ${localModelFile.absolutePath}")
            } else {
                Log.d(TAG, "Using existing local model: ${localModelFile.absolutePath}")
            }

            val modelParams = ModelParameters()
                .setModel(localModelFile.absolutePath)
                .setGpuLayers(LlamaConfig.GPU_LAYERS_ANDROID)

            Log.d(TAG, "Initializing LlamaModel with GGUF: ${localModelFile.absolutePath}")
            llamaModel = LlamaModel(modelParams)
            Log.i(TAG, "LlamaModel initialized successfully.")
            LlamaInitResult.Success
        } catch (e: IOException) {
            Log.e(TAG, "Failed to copy or load GGUF model from assets.", e)
            llamaModel = null
            LlamaInitResult.Failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error while initializing LlamaModel.", e)
            llamaModel = null
            LlamaInitResult.Failure(e)
        }
    }

    /**
     * 입력 텍스트에 대해 LLM으로 분석(추론) 수행.
     *
     * @param input 사용자 입력 (Rule/URL 요약 + 메시지 등). [QwenPromptBuilder]로 ChatML 래핑됨
     * @return [LlamaAnalyzeResult] — Success(text), NotInitialized, EmptyInput, Error
     */
    override suspend fun analyzeText(input: String): LlamaAnalyzeResult = withContext(Dispatchers.IO) {
        val model = llamaModel
        if (model == null) {
            Log.w(TAG, "analyzeText() called before initModel().")
            return@withContext LlamaAnalyzeResult.NotInitialized
        }

        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return@withContext LlamaAnalyzeResult.EmptyInput
        }

        val prompt = QwenPromptBuilder.buildPrompt(trimmed)

        try {
            Log.d(TAG, "Starting inference. Prompt length=${prompt.length}")

            val inferParams = InferenceParameters(prompt)
                .setTemperature(LlamaConfig.DEFAULT_TEMPERATURE)
                .setPenalizeNl(LlamaConfig.DEFAULT_PENALIZE_NL)
                .setStopStrings(LlamaConfig.STOP_STRING_IM_END)
                .setNPredict(LlamaConfig.DEFAULT_MAX_TOKENS)

            val sb = StringBuilder()
            for (output in model.generate(inferParams)) {
                sb.append(output.toString())
            }

            val result = sb.toString().trim()
            Log.d(TAG, "LLM result: ${result.take(200)}")

            if (result.isBlank()) {
                LlamaAnalyzeResult.Success(LlamaConfig.FALLBACK_MESSAGE)
            } else {
                LlamaAnalyzeResult.Success(result)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during LLM inference.", e)
            LlamaAnalyzeResult.Error(e)
        }
    }

    /**
     * 이전 API 호환: 분석 결과를 문자열로 반환.
     * 실패 시 [LlamaConfig.FALLBACK_MESSAGE] 반환.
     */
    suspend fun analyzeTextAsString(input: String): String = when (val result = analyzeText(input)) {
        is LlamaAnalyzeResult.Success -> result.text
        else -> LlamaConfig.FALLBACK_MESSAGE
    }

    /**
     * 모델과 네이티브 리소스 해제.
     */
    override fun close() {
        try {
            llamaModel?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error while closing LlamaModel.", e)
        } finally {
            llamaModel = null
        }
    }
}
