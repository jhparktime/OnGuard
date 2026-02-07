package com.onguard.domain.model

/**
 * 전화번호 분석 결과
 *
 * Counter Scam 112 API 조회 결과 및 로컬 패턴 분석 결과를 통합합니다.
 *
 * @property extractedPhones 텍스트에서 추출된 전화번호 목록
 * @property scamPhones Counter Scam 112 DB에 등록된 전화번호 목록
 * @property reasons 탐지 사유 목록
 * @property riskScore 종합 위험도 점수 (0.0 ~ 1.0)
 * @property voicePhishingCount 보이스피싱 신고 건수
 * @property smsPhishingCount 스미싱 신고 건수
 * @property isSuspiciousPrefix 의심 전화번호 대역 (070, 050 등) 포함 여부
 */
data class PhoneAnalysisResult(
    val extractedPhones: List<String> = emptyList(),
    val scamPhones: List<String> = emptyList(),
    val reasons: List<String> = emptyList(),
    val riskScore: Float = 0f,
    val voicePhishingCount: Int = 0,
    val smsPhishingCount: Int = 0,
    val isSuspiciousPrefix: Boolean = false
) {
    companion object {
        /** 빈 결과 (전화번호 없음) */
        val EMPTY = PhoneAnalysisResult()

        /** API 호출 실패 시 반환할 결과 */
        fun apiError(extractedPhones: List<String>) = PhoneAnalysisResult(
            extractedPhones = extractedPhones,
            reasons = emptyList(),
            riskScore = 0f
        )
    }

    /** Counter Scam 112 DB에 등록된 번호가 있는지 여부 */
    val hasScamPhones: Boolean
        get() = scamPhones.isNotEmpty()

    /** 신고 이력이 있는지 여부 (보이스피싱 또는 스미싱) */
    val hasReportHistory: Boolean
        get() = voicePhishingCount > 0 || smsPhishingCount > 0
}
