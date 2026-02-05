package com.onguard.llm.stub

/**
 * 간단한 stub 구현입니다.
 *
 * 실제 sherpa-onnx Android 라이브러리를 사용할 때는
 * 이 파일을 삭제하고 정식 라이브러리를 의존성에 추가하면 됩니다.
 */
class OfflineLlmConfig private constructor(
    val model: String,
    val tokenizer: String,
) {

    class Builder {
        private var model: String = ""
        private var tokenizer: String = ""

        fun setModel(path: String): Builder = apply { this.model = path }

        fun setTokenizer(path: String): Builder = apply { this.tokenizer = path }

        fun build(): OfflineLlmConfig = OfflineLlmConfig(
            model = model,
            tokenizer = tokenizer,
        )
    }

    companion object {
        @JvmStatic
        fun builder(): Builder = Builder()
    }
}

/**
 * sherpa-onnx OfflineLlm API와 호환되는 최소 stub입니다.
 *
 * - generate: 간단한 안내 메시지를 반환
 * - release: no-op
 */
class OfflineLlm(
    private val config: OfflineLlmConfig,
) {

    /**
     * 실제 sherpa-onnx 기반 LLM 대신, 현재는 안내 텍스트만 반환합니다.
     *
     * 나중에 정식 sherpa-onnx 라이브러리를 붙일 때는
     * 이 클래스를 제거하고 공식 OfflineLlm 구현을 사용하면 됩니다.
     */
    fun generate(prompt: String): String {
        val preview = prompt.take(80)
        return buildString {
            appendLine("[LLM 스텁] 현재 sherpa-onnx 네이티브 라이브러리가 연결되지 않은 상태입니다.")
            appendLine("모델 경로: ${config.model}")
            appendLine("토크나이저 경로: ${config.tokenizer}")
            appendLine("입력 일부: $preview")
        }.trimEnd()
    }

    fun release() {
        // no-op
    }
}

