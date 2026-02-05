package com.onguard.detector

import com.onguard.domain.model.ScamAnalysis

/**
 * LLM 기반 텍스트 스캠 분석기 계약.
 *
 * HybridScamDetector가 의존하는 인터페이스로, 테스트 시 mock 주입이 가능하다.
 */
interface LLMScamAnalyzer {

    suspend fun initialize(): Boolean
    fun isAvailable(): Boolean
    suspend fun analyze(text: String, context: LLMScamDetector.LlmContext? = null): ScamAnalysis?
    fun close()
}
