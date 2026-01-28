package com.dealguard.domain.model

/**
 * 스캠 분석 결과를 담는 데이터 클래스
 *
 * @property isScam 스캠 여부
 * @property confidence 신뢰도 (0.0 ~ 1.0)
 * @property reasons 탐지 이유 목록
 * @property detectedKeywords 탐지된 키워드 목록
 * @property detectionMethod 탐지에 사용된 방법
 * @property scamType 스캠 유형 (투자사기, 중고거래사기 등)
 * @property warningMessage 사용자에게 표시할 경고 메시지 (LLM 생성)
 * @property suspiciousParts 의심되는 문구 인용 목록
 */
data class ScamAnalysis(
    val isScam: Boolean,
    val confidence: Float,
    val reasons: List<String>,
    val detectedKeywords: List<String> = emptyList(),
    val detectionMethod: DetectionMethod = DetectionMethod.RULE_BASED,
    val scamType: ScamType = ScamType.UNKNOWN,
    val warningMessage: String? = null,
    val suspiciousParts: List<String> = emptyList()
)

/**
 * 탐지 방법
 */
enum class DetectionMethod {
    RULE_BASED,
    ML_CLASSIFIER,
    HYBRID,
    EXTERNAL_DB,
    LLM  // 새로 추가: LLM 기반 탐지
}

/**
 * 스캠 유형
 */
enum class ScamType {
    UNKNOWN,           // 알 수 없음
    INVESTMENT,        // 투자 사기
    USED_TRADE,        // 중고거래 사기
    PHISHING,          // 피싱
    IMPERSONATION,     // 사칭
    ROMANCE,           // 로맨스 스캠
    LOAN,              // 대출 사기
    SAFE               // 정상 (스캠 아님)
}
