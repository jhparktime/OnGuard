//app/src/main/java/com/onguard/presentation/ui/dashboard/DashboardState.kt

package com.onguard.presentation.ui.dashboard

// 디자인에 표시되는 모든 통계 데이터를 포함하는 UI 상태 클래스
data class DashboardUiState(
    val status: SecurityStatus = SecurityStatus.PROTECTED,
    val totalDetectionCount: Int = 3110, // 메인 거대 숫자
    
    // 상단 3개 카드 통계
    val highRiskCount: Int = 10,
    val mediumRiskCount: Int = 100,
    val lowRiskCount: Int = 1000,

    // 중간 차트 카드 통계
    val totalKeywords: Int = 100,
    val totalDetectionValue: Int = 5000,
    val totalDetectionUnit: String = "시간",

    // 하단 일일 위험 탐지 섹션
    val dailyStats: DailyRiskStats = DailyRiskStats(),
    
    // 보호 상태 관련
    val isProtected: Boolean = false, // 권한 + 탐지 활성화 여부
    val isAccessibilityEnabled: Boolean = false,
    val isOverlayEnabled: Boolean = false,
    val isDetectionEnabled: Boolean = false,
    
    val isLoading: Boolean = false
)

enum class SecurityStatus {
    PROTECTED, UNPROTECTED
}

// 일일 통계 및 상세 내역
data class DailyRiskStats(
    val cumulativeCount: Int = 3000, // 원형 게이지 중앙 숫자
    // 각 위험도별 비율 (0.0 ~ 1.0)
    val highRiskRatio: Float = 0.333f,
    val mediumRiskRatio: Float = 0.333f,
    val lowRiskRatio: Float = 0.333f,
    // 하단 상세 카드 데이터
    val highRiskDetail: RiskDetail = RiskDetail(
        count = 1000,
        tags = listOf("금전적인 정보 피해", "개인정보 요구")
    ),
    val mediumRiskDetail: RiskDetail = RiskDetail(
        count = 1000,
        tags = listOf("불법 금융 거래", "개인정보 요구")
    ),
    val lowRiskDetail: RiskDetail = RiskDetail(
        count = 1000,
        tags = listOf("공식 유도", "미끼성 낚시")
    )
)

data class RiskDetail(
    val count: Int,
    val tags: List<String> // "개인정보 요구" 같은 태그들
)