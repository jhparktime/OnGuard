package com.onguard.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 탐지 설정 저장소 (DataStore 기반)
 *
 * 다음 설정을 관리:
 * - 전역 탐지 활성화/비활성화
 * - 시간 제한 비활성화 (일시 중지)
 * - 앱별 탐지 설정
 */

private val Context.detectionSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "detection_settings"
)

/**
 * 탐지 설정 데이터 클래스
 */
data class DetectionSettings(
    /** 전역 탐지 활성화 여부 */
    val isDetectionEnabled: Boolean = true,

    /** 일시 중지 종료 시각 (epoch ms), 0이면 일시 중지 아님 */
    val pauseUntilTimestamp: Long = 0L,

    /** 비활성화된 앱 패키지 목록 */
    val disabledApps: Set<String> = emptySet(),

    /** 현재 세션 시작/재개 시각 (epoch ms), 0이면 정지/일시정지 */
    val detectionStartTime: Long = 0L,

    /** 현재 세션 누적 시간 (밀리초) - 일시정지 때마다 누적됨 */
    val sessionAccumulatedTime: Long = 0L,

    /** 전체 누적 탐지 시간 (밀리초) - 대시보드 표시용 */
    val totalAccumulatedTime: Long = 0L,

    /** 제어 위젯 활성화 여부 */
    val isWidgetEnabled: Boolean = true
) {
    companion object {
        val SUPPORTED_PACKAGES = setOf(
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
     * 탐지를 시작할 수 있는 상태인지 확인 (권한 + 앱 선택)
     */
    fun canStartDetection(isAccessibilityEnabled: Boolean, isOverlayEnabled: Boolean): Boolean {
        // 1. 필수 권한 체크
        if (!isAccessibilityEnabled || !isOverlayEnabled) return false
        
        // 2. 최소 하나 이상의 앱이 활성화되어 있는지 체크
        // 모든 지원 앱이 disabledApps에 들어있으면 시작 불가
        if (disabledApps.containsAll(SUPPORTED_PACKAGES)) return false
        
        return true
    }

    /**
     * 현재 탐지가 활성 상태인지 확인
     *
     * - 전역 비활성화: false
     * - 일시 중지 중: false
     * - 그 외: true
     */
    fun isActiveNow(): Boolean {
        if (!isDetectionEnabled) return false
        if (pauseUntilTimestamp > 0 && System.currentTimeMillis() < pauseUntilTimestamp) {
            return false
        }
        return true
    }

    /**
     * 특정 앱에 대해 탐지가 활성 상태인지 확인
     */
    fun isActiveForApp(packageName: String): Boolean {
        if (!isActiveNow()) return false
        return packageName !in disabledApps
    }

    /**
     * 일시 중지 남은 시간 (밀리초)
     */
    fun remainingPauseTime(): Long {
        if (pauseUntilTimestamp <= 0) return 0
        val remaining = pauseUntilTimestamp - System.currentTimeMillis()
        return if (remaining > 0) remaining else 0
    }

    /**
     * Chronometer의 base 필드에 설정할 값을 계산 (SystemClock.elapsedRealtime() 기준)
     * 알림, 위젯, 앱 대시보드 모두 이 값을 공유하여 동기화함.
     */
    fun calculateChronometerBase(elapsedRealtime: Long = android.os.SystemClock.elapsedRealtime()): Long {
        val now = System.currentTimeMillis()
        val currentSegment = if (isActiveNow() && detectionStartTime > 0) {
            now - detectionStartTime
        } else {
            0L
        }
        val totalSessionMs = sessionAccumulatedTime + currentSegment
        return elapsedRealtime - totalSessionMs
    }
}

@Singleton
class DetectionSettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.detectionSettingsDataStore

    companion object {
        private val KEY_DETECTION_ENABLED = booleanPreferencesKey("detection_enabled")
        private val KEY_PAUSE_UNTIL = longPreferencesKey("pause_until_timestamp")
        private val KEY_DISABLED_APPS = stringSetPreferencesKey("disabled_apps")
        private val KEY_DETECTION_START_TIME = longPreferencesKey("detection_start_time")
        private val KEY_SESSION_ACCUMULATED_TIME = longPreferencesKey("session_accumulated_time")
        private val KEY_TOTAL_ACCUMULATED_TIME = longPreferencesKey("total_accumulated_time")
        private val KEY_WIDGET_ENABLED = booleanPreferencesKey("widget_enabled")
    }

    /**
     * 현재 설정을 Flow로 관찰
     */
    val settingsFlow: Flow<DetectionSettings> = dataStore.data.map { preferences ->
        DetectionSettings(
            isDetectionEnabled = preferences[KEY_DETECTION_ENABLED] ?: true,
            pauseUntilTimestamp = preferences[KEY_PAUSE_UNTIL] ?: 0L,
            disabledApps = preferences[KEY_DISABLED_APPS] ?: emptySet(),
            detectionStartTime = preferences[KEY_DETECTION_START_TIME] ?: 0L,
            sessionAccumulatedTime = preferences[KEY_SESSION_ACCUMULATED_TIME] ?: 0L,
            totalAccumulatedTime = preferences[KEY_TOTAL_ACCUMULATED_TIME] ?: 0L,
            isWidgetEnabled = preferences[KEY_WIDGET_ENABLED] ?: true
        )
    }

    /**
     * 전역 탐지 활성화/비활성화 설정
     */
    suspend fun setDetectionEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_DETECTION_ENABLED] = enabled
            val now = System.currentTimeMillis()
            
            if (enabled) {
                // 활성화 시: 시작 시간 기록, 일시 정지 해제 (세션 시간은 0에서 시작)
                preferences[KEY_PAUSE_UNTIL] = 0L
                preferences[KEY_DETECTION_START_TIME] = now
                preferences[KEY_SESSION_ACCUMULATED_TIME] = 0L
            } else {
                // 비활성화 시 (완전 정지): 마지막 구간 시간 누적 후 리셋
                val startTime = preferences[KEY_DETECTION_START_TIME] ?: 0L
                if (startTime > 0) {
                    val diff = now - startTime
                    val total = preferences[KEY_TOTAL_ACCUMULATED_TIME] ?: 0L
                    preferences[KEY_TOTAL_ACCUMULATED_TIME] = total + diff
                }
                // 세션 및 시작 시간 리셋
                preferences[KEY_DETECTION_START_TIME] = 0L
                preferences[KEY_SESSION_ACCUMULATED_TIME] = 0L
                preferences[KEY_PAUSE_UNTIL] = 0L
            }
        }
    }

    /**
     * 제어 위젯 활성화/비활성화 설정
     */
    suspend fun setWidgetEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_WIDGET_ENABLED] = enabled
        }
    }

    /**
     * 일시 중지 설정 (시간 제한 비활성화)
     *
     * @param durationMinutes 일시 중지 시간 (분), 0이면 해제
     */
    suspend fun pauseDetection(durationMinutes: Int) {
        dataStore.edit { preferences ->
            val now = System.currentTimeMillis()
            
            // 일시 중지 시작: 현재까지의 시간 누적 처리
            val startTime = preferences[KEY_DETECTION_START_TIME] ?: 0L
            if (startTime > 0) {
                val diff = now - startTime
                
                // 세션 누적
                val sessionAcc = preferences[KEY_SESSION_ACCUMULATED_TIME] ?: 0L
                preferences[KEY_SESSION_ACCUMULATED_TIME] = sessionAcc + diff
                
                // 전체 누적
                val totalAcc = preferences[KEY_TOTAL_ACCUMULATED_TIME] ?: 0L
                preferences[KEY_TOTAL_ACCUMULATED_TIME] = totalAcc + diff
                
                // 타이머 정지 상태로 변경
                preferences[KEY_DETECTION_START_TIME] = 0L
            }

            if (durationMinutes > 0) {
                val pauseUntil = now + (durationMinutes * 60 * 1000L)
                preferences[KEY_PAUSE_UNTIL] = pauseUntil
            } else {
                preferences[KEY_PAUSE_UNTIL] = 0L
            }
        }
    }

    /**
     * 일시 중지 해제
     */
    suspend fun resumeDetection() {
        dataStore.edit { preferences ->
            preferences[KEY_PAUSE_UNTIL] = 0L
            // 재개 시 타이머 다시 시작
            preferences[KEY_DETECTION_START_TIME] = System.currentTimeMillis()
        }
    }

    /**
     * 특정 앱 탐지 활성화/비활성화
     */
    suspend fun setAppEnabled(packageName: String, enabled: Boolean) {
        dataStore.edit { preferences ->
            val currentDisabled = preferences[KEY_DISABLED_APPS] ?: emptySet()
            preferences[KEY_DISABLED_APPS] = if (enabled) {
                currentDisabled - packageName
            } else {
                currentDisabled + packageName
            }
        }
    }

    /**
     * 모든 앱 탐지 활성화 (비활성화 목록 초기화)
     */
    suspend fun enableAllApps() {
        dataStore.edit { preferences ->
            preferences[KEY_DISABLED_APPS] = emptySet()
        }
    }

    /**
     * 여러 앱 탐지 일괄 활성화
     */
    suspend fun enableApps(packageNames: Collection<String>) {
        dataStore.edit { preferences ->
            val currentDisabled = preferences[KEY_DISABLED_APPS] ?: emptySet()
            preferences[KEY_DISABLED_APPS] = currentDisabled - packageNames.toSet()
        }
    }
}
