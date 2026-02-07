package com.onguard.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onguard.domain.repository.ScamAlertRepository
import com.onguard.presentation.ui.dashboard.DailyRiskStats
import com.onguard.presentation.ui.dashboard.DashboardUiState
import com.onguard.presentation.ui.dashboard.RiskDetail
import com.onguard.presentation.ui.dashboard.SecurityStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val repository: ScamAlertRepository
) : AndroidViewModel(application) {

    // 1. Repository에서 데이터를 가져와서 UI State로 변환하는 파이프라인
    val uiState: StateFlow<DashboardUiState> = repository.getAllAlerts()
        .map { alerts ->
            // [데이터 변환 로직] List<ScamAlert> -> DashboardUiState
            
            // 위험도별 카운팅
            val highRiskCount = alerts.count { it.confidence >= 0.7f }
            val mediumRiskCount = alerts.count { it.confidence in 0.4f..0.69f }
            val lowRiskCount = alerts.count { it.confidence < 0.4f }
            
            // 키워드 총합 계산 (중복 포함 or 제거 정책에 따라 조정)
            val totalKeywords = alerts.sumOf { it.detectedKeywords.size }
            
            // 상태 결정 (최근 24시간 내 고위험 있으면 UNPROTECTED)
            val isSafe = highRiskCount == 0 
            
            // 매핑 결과 반환
            DashboardUiState(
                status = if (isSafe) SecurityStatus.PROTECTED else SecurityStatus.UNPROTECTED,
                totalDetectionCount = alerts.size,
                
                highRiskCount = highRiskCount,
                mediumRiskCount = mediumRiskCount,
                lowRiskCount = lowRiskCount,
                
                totalKeywords = totalKeywords,
                totalDetectionHours = 120, // 예시: 실제로는 서비스 실행 시간을 저장해서 가져와야 함
                
                // 하단 일일 통계 (데모용 계산 로직)
                dailyStats = DailyRiskStats(
                    cumulativeCount = alerts.size,
                    highRiskRatio = if (alerts.isNotEmpty()) highRiskCount.toFloat() / alerts.size else 0f,
                    mediumRiskRatio = if (alerts.isNotEmpty()) mediumRiskCount.toFloat() / alerts.size else 0f,
                    lowRiskRatio = if (alerts.isNotEmpty()) lowRiskCount.toFloat() / alerts.size else 0f,
                    
                    // 상세 내역에는 실제 가장 많이 탐지된 키워드를 넣음
                    highRiskDetail = RiskDetail(highRiskCount, getTopKeywords(alerts, 0.7f)),
                    mediumRiskDetail = RiskDetail(mediumRiskCount, getTopKeywords(alerts, 0.4f, 0.69f)),
                    lowRiskDetail = RiskDetail(lowRiskCount, getTopKeywords(alerts, 0.0f, 0.39f))
                )
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DashboardUiState(isLoading = true)
        )

    // 헬퍼 함수: 특정 위험도 구간의 상위 키워드 추출
    private fun getTopKeywords(alerts: List<com.onguard.domain.model.ScamAlert>, minConf: Float, maxConf: Float = 1.0f): List<String> {
        return alerts
            .filter { it.confidence in minConf..maxConf }
            .flatMap { it.detectedKeywords }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(3)
            .map { it.key }
    }
}