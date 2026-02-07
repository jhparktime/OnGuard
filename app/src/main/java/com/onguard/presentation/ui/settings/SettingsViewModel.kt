package com.onguard.presentation.ui.settings

import android.app.Application
import android.content.Context
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onguard.data.local.DetectionSettings
import com.onguard.data.local.DetectionSettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 설정 화면 UI 상태
 */
data class SettingsUiState(
    val settings: DetectionSettings = DetectionSettings(),
    val isLoading: Boolean = true,
    val pauseDurationMinutes: Int? = null,  // 선택된 일시 중지 시간
    val showPauseDialog: Boolean = false,
    val isAccessibilityEnabled: Boolean = false,
    val isOverlayEnabled: Boolean = false
)

/**
 * 일시 중지 옵션
 */
enum class PauseDuration(val minutes: Int, val label: String) {
    MINUTES_15(15, "15분"),
    MINUTES_30(30, "30분"),
    HOUR_1(60, "1시간"),
    HOURS_2(120, "2시간"),
    HOURS_4(240, "4시간"),
    HOURS_8(480, "8시간")
}

/**
 * 설정 화면 ViewModel
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsStore: DetectionSettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        observeSettings()
        checkPermissions()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsStore.settingsFlow.collect { settings ->
                _uiState.update { it.copy(settings = settings, isLoading = false) }
            }
        }
    }

    /**
     * 전역 탐지 활성화/비활성화
     */
    /**
     * 전역 탐지 활성화/비활성화
     */
    fun setDetectionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                // 활성화 시도 시 권한 재확인
                checkPermissions()
                val currentState = _uiState.value
                if (!currentState.isAccessibilityEnabled || !currentState.isOverlayEnabled) {
                    // 권한 없으면 활성화 불가 (UI에서 메시지 등을 처리해야 할 수도 있음)
                    // 현재는 그냥 무시하거나 토스트 메시지가 필요할 수 있음.
                    // 간단히 권한이 있을 때만 활성화
                    return@launch
                }
                
                // 만약 모든 앱이 비활성화 상태라면, 탐지 활성화 시 모든 앱도 같이 활성화
                if (currentState.settings.disabledApps.containsAll(SUPPORTED_PACKAGES)) {
                    settingsStore.enableAllApps()
                }
            }
            settingsStore.setDetectionEnabled(enabled)
        }
    }

    /**
     * 일시 중지 다이얼로그 표시
     */
    fun showPauseDialog() {
        _uiState.update { it.copy(showPauseDialog = true) }
    }

    /**
     * 일시 중지 다이얼로그 닫기
     */
    fun dismissPauseDialog() {
        _uiState.update { it.copy(showPauseDialog = false) }
    }

    /**
     * 탐지 일시 중지
     */
    fun pauseDetection(durationMinutes: Int) {
        viewModelScope.launch {
            settingsStore.pauseDetection(durationMinutes)
            _uiState.update { it.copy(showPauseDialog = false) }
        }
    }

    /**
     * 일시 중지 해제 (탐지 재개)
     */
    fun resumeDetection() {
        viewModelScope.launch {
            settingsStore.resumeDetection()
        }
    }

    /**
     * 특정 앱 탐지 설정
     */
    fun setAppEnabled(packageName: String, enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setAppEnabled(packageName, enabled)
            
            // 앱 비활성화 시, 모든 앱이 비활성화되었는지 확인
            if (!enabled) {
                // 현재 설정 가져오기 (비동기 반영 고려, 잠시 대기하거나 flow collect 필요할 수 있음)
                // 하지만 DataStore는 내부적으로 직렬화되므로 다음 읽기에서 반영됨
                // 여기서는 간단히 flow의 최신 값을 바로 확인 (stateFlow는 아직 업데이트 안 됐을 수도 있음)
                // 안전하게 DataStore에서 직접 확인하거나, 잠시 후 확인
                
                // 더 나은 방법: settingsStore에서 반환값을 받거나, Flow를 구독하고 있는 곳에서 처리
                // 하지만 ViewModel에서 처리하는 게 깔끔함.
                // 여기서는 간단히 SUPPORTED_PACKAGES 전체가 disabledApps에 포함되는지 확인
                
                // 주의: settingsStore 업데이트 직후 flow가 방출되기까지 시간이 걸릴 수 있음.
                // 따라서 로컬 변수로 예측하거나, 잠시 delay를 줄 수도 있지만,
                // 가장 확실한 건 DataStore의 edit 블록 내에서 처리하거나,
                // 여기서 약간의 지연 후 상태 확인.
                
                // 일단 간단히 구현: 현재 state에서 예측 (이미 disabledApps에 추가됨)
                // 만약 동시성 문제가 있다면 settingsStore 내부에 로직을 넣는 게 좋음.
                // 하지만 요구사항이 단순하므로 ViewModel에서 처리.
                
                val currentDisabled = _uiState.value.settings.disabledApps + packageName
                if (currentDisabled.containsAll(SUPPORTED_PACKAGES)) {
                    setDetectionEnabled(false)
                }
            }
        }
    }
    
    companion object {
        private val SUPPORTED_PACKAGES = setOf(
            "com.kakao.talk",
            "org.telegram.messenger",
            "jp.naver.line.android",
            "com.facebook.orca",
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.instagram.android",
            "com.whatsapp",
            "com.discord",
            "kr.co.daangn"
        )
    }

    /**
     * 모든 앱 탐지 활성화
     */
    fun enableAllApps() {
        viewModelScope.launch {
            settingsStore.enableAllApps()
        }
    }

    /**
     * 여러 앱 탐지 일괄 활성화
     */
    fun enableApps(packageNames: Collection<String>) {
        viewModelScope.launch {
            settingsStore.enableApps(packageNames)
        }
    }

    /**
     * 권한 상태 확인
     */
    fun checkPermissions() {
        val isAccessibilityEnabled = isAccessibilityServiceEnabled()
        val isOverlayEnabled = Settings.canDrawOverlays(context)
        
        _uiState.update {
            it.copy(
                isAccessibilityEnabled = isAccessibilityEnabled,
                isOverlayEnabled = isOverlayEnabled
            )
        }

        // 권한이 하나라도 없으면 스캠 탐지 자동 비활성화
        if (!isAccessibilityEnabled || !isOverlayEnabled) {
            viewModelScope.launch {
                settingsStore.setDetectionEnabled(false)
            }
        }
    }

    /**
     * 접근성 서비스 활성화 여부 확인
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        val serviceName = "${context.packageName}/com.onguard.service.ScamDetectionAccessibilityService"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return enabledServices?.contains(serviceName) == true
    }
}
