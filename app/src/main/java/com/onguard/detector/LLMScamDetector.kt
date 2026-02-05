package com.onguard.detector

import android.content.Context
import android.util.Log
import com.onguard.domain.model.ScamAnalysis
import com.onguard.llm.LlamaAnalyzeResult
import com.onguard.llm.LlamaInitResult
import com.onguard.llm.LlamaManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LLM 기반 스캠 탐지기.
 *
 * llama.cpp + Qwen([LlamaManager]) GGUF로 채팅 메시지를 분석해
 * 스캠 여부·신뢰도·경고 메시지·의심 문구를 생성한다.
 * 모델 미초기화/실패 시 [analyze]는 null을 반환하며 Rule 기반만 사용된다.
 */
@Singleton
class LLMScamDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val llamaManager: LlamaManager
) : LLMScamAnalyzer {
    companion object {
        private const val TAG = "LLMScamDetector"
        private const val MAX_INPUT_CHARS = 1500
        private const val MAX_CONTEXT_LIST_ITEMS = 8
    }

    /**
     * Rule/URL 1차 분석 결과 등 LLM에 넘길 추가 컨텍스트.
     */
    data class LlmContext(
        val ruleConfidence: Float? = null,
        val ruleReasons: List<String> = emptyList(),
        val detectedKeywords: List<String> = emptyList(),
        val urls: List<String> = emptyList(),
        val suspiciousUrls: List<String> = emptyList(),
        val urlReasons: List<String> = emptyList()
    )

    private var isInitialized = false
    private var initializationAttempted = false

    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "=== LLM Initialization (llama.cpp + Qwen) ===")
        if (isInitialized) {
            Log.d(TAG, "LLM already initialized.")
            return@withContext true
        }
        try {
            when (llamaManager.initModel()) {
                is LlamaInitResult.Success -> {
                    isInitialized = true
                    initializationAttempted = true
                    Log.i(TAG, "LLM backend: llama.cpp + Qwen (LlamaManager)")
                    true
                }
                is LlamaInitResult.Failure -> {
                    Log.w(TAG, "LlamaManager init failed.")
                    false
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "LlamaManager init error: ${e.message}", e)
            false
        }
    }

    override fun isAvailable(): Boolean = isInitialized

    /**
     * 주어진 텍스트를 LLM으로 분석해 [ScamAnalysis] 반환.
     *
     * @param text 분석할 채팅 메시지
     * @param llmContext Rule/URL 1차 분석 등 추가 컨텍스트 (선택)
     * @return 분석 결과. 미사용/빈 응답/파싱 실패 시 null
     */
    override suspend fun analyze(text: String, llmContext: LlmContext?): ScamAnalysis? = withContext(Dispatchers.Default) {
        val input = if (text.length > MAX_INPUT_CHARS) {
            Log.d(TAG, "Input truncated: ${text.length} -> $MAX_INPUT_CHARS chars")
            text.take(MAX_INPUT_CHARS)
        } else text

        if (!isAvailable()) {
            Log.w(TAG, "LLM not available, skipping analysis")
            return@withContext null
        }

        try {
            val userInput = buildLlamaUserInput(input, llmContext)
            when (val result = llamaManager.analyzeText(userInput)) {
                is LlamaAnalyzeResult.Success -> {
                    val response = result.text
                    if (response.isBlank() || response == "분석 실패") {
                        Log.w(TAG, "LlamaManager returned empty or fallback")
                        return@withContext null
                    }
                    val parsed = LlamaResponseParser.parseResponse(response)
                    if (parsed != null) {
                        Log.d(TAG, "LLM analysis success: isScam=${parsed.isScam}, confidence=${parsed.confidence}")
                    }
                    parsed
                }
                is LlamaAnalyzeResult.NotInitialized,
                is LlamaAnalyzeResult.EmptyInput,
                is LlamaAnalyzeResult.Error -> {
                    Log.w(TAG, "LlamaManager analyze failed: $result")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "LLM analyze error", e)
            null
        }
    }

    private fun buildLlamaUserInput(text: String, llmContext: LlmContext?): String {
        val contextBlock = buildContextBlock(llmContext)
        return if (contextBlock.isNotBlank()) {
            "$contextBlock\n\n[메시지]\n$text"
        } else {
            "[메시지]\n$text"
        }
    }

    private fun buildContextBlock(llmContext: LlmContext?): String {
        if (llmContext == null) return ""
        return buildString {
            if (llmContext.ruleConfidence != null || llmContext.ruleReasons.isNotEmpty() || llmContext.detectedKeywords.isNotEmpty()) {
                appendLine("[Rule-based 1차 분석 요약]")
                llmContext.ruleConfidence?.let { appendLine("- rule_confidence: $it") }
                if (llmContext.detectedKeywords.isNotEmpty()) {
                    appendLine("- detected_keywords: ${llmContext.detectedKeywords.take(MAX_CONTEXT_LIST_ITEMS).joinToString()}")
                }
                if (llmContext.ruleReasons.isNotEmpty()) {
                    appendLine("- rule_reasons:")
                    llmContext.ruleReasons.take(MAX_CONTEXT_LIST_ITEMS).forEach { appendLine("  - $it") }
                }
                appendLine()
            }
            if (llmContext.urls.isNotEmpty() || llmContext.suspiciousUrls.isNotEmpty() || llmContext.urlReasons.isNotEmpty()) {
                appendLine("[URL/DB 기반 분석 요약]")
                if (llmContext.urls.isNotEmpty()) appendLine("- urls: ${llmContext.urls.take(MAX_CONTEXT_LIST_ITEMS).joinToString()}")
                if (llmContext.suspiciousUrls.isNotEmpty()) appendLine("- suspicious_urls: ${llmContext.suspiciousUrls.take(MAX_CONTEXT_LIST_ITEMS).joinToString()}")
                if (llmContext.urlReasons.isNotEmpty()) {
                    appendLine("- url_reasons:")
                    llmContext.urlReasons.take(MAX_CONTEXT_LIST_ITEMS).forEach { appendLine("  - $it") }
                }
            }
        }.trimEnd()
    }

    override fun close() {
        try {
            llamaManager.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing LlamaManager", e)
        }
        isInitialized = false
        initializationAttempted = false
    }
}
