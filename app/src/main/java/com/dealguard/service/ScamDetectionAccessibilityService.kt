package com.dealguard.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.dealguard.detector.HybridScamDetector
import com.dealguard.domain.model.ScamAnalysis
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import javax.inject.Inject

@AndroidEntryPoint
class ScamDetectionAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var scamDetector: HybridScamDetector

    private val targetPackages = setOf(
        // 메신저 앱
        "com.kakao.talk",                           // 카카오톡
        "org.telegram.messenger",                   // 텔레그램
        "com.whatsapp",                             // 왓츠앱
        "com.facebook.orca",                        // 페이스북 메신저
        "com.instagram.android",                    // 인스타그램

        // SMS/MMS 앱
        "com.google.android.apps.messaging",        // Google Messages
        "com.samsung.android.messaging",            // Samsung Messages
        "com.android.mms",                          // 기본 메시지 앱

        // 거래 플랫폼
        "kr.co.daangn",                             // 당근마켓
        "com.nhn.android.search",                   // 네이버 (채팅)

        // 추가 메신저
        "jp.naver.line.android",                    // 라인
        "com.tencent.mm",                           // 위챗
        "com.viber.voip",                           // 바이버
        "kik.android",                              // 킥
        "com.skype.raider",                         // 스카이프
        "com.discord",                              // 디스코드
        "com.snapchat.android"                      // 스냅챗
    )

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    private val handler = Handler(Looper.getMainLooper())
    private var debounceJob: Job? = null
    private var lastProcessedText: WeakReference<String>? = null

    companion object {
        private const val TAG = "ScamDetectionService"

        // 디바운스 지연: 100ms
        // - 사용자 타이핑 중 과도한 분석 방지
        // - 100ms 미만: CPU/배터리 과다 사용
        // - 200ms 초과: UX 반응성 저하
        private const val DEBOUNCE_DELAY_MS = 100L

        // 최소 텍스트 길이: 10자
        // - 너무 짧은 텍스트는 스캠 판정 불가
        // - "안녕하세요"(5자) 같은 일반 인사 필터링
        // - 스캠 메시지는 보통 20자 이상
        private const val MIN_TEXT_LENGTH = 10

        // 스캠 임계값: 0.5 (50%)
        // - 오탐(false positive) 최소화와 미탐(false negative) 균형점
        // - 0.3 이하: 오탐 증가 (일반 메시지도 스캠 판정)
        // - 0.7 이상: 미탐 증가 (실제 스캠 놓침)
        // - KeywordMatcher와 연동: CRITICAL 2개 조합(0.8) 또는 CRITICAL+HIGH(0.65)
        private const val SCAM_THRESHOLD = 0.5f
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Accessibility Service Connected")

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }

        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // 패키지 체크
        val packageName = event.packageName?.toString()
        if (packageName !in targetPackages) return

        // 이벤트 타입 체크
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                processEvent(event)
            }
            else -> return
        }
    }

    private fun processEvent(event: AccessibilityEvent) {
        // Debouncing: 이전 작업 취소
        debounceJob?.cancel()

        debounceJob = serviceScope.launch {
            delay(DEBOUNCE_DELAY_MS)

            val node = rootInActiveWindow ?: return@launch

            try {
                val extractedText = extractTextFromNode(node)

                // 최소 길이 체크
                if (extractedText.length < MIN_TEXT_LENGTH) return@launch

                // 중복 텍스트 체크 (캐시)
                val lastText = lastProcessedText?.get()
                if (extractedText == lastText) return@launch

                // 새로운 텍스트 저장 (WeakReference)
                lastProcessedText = WeakReference(extractedText)

                Log.d(TAG, "Extracted text (${extractedText.length} chars): ${extractedText.take(100)}...")

                // 스캠 분석
                analyzeForScam(extractedText, event.packageName.toString())

            } catch (e: Exception) {
                Log.e(TAG, "Error processing event", e)
            } finally {
                node.recycle()
            }
        }
    }

    private fun extractTextFromNode(node: AccessibilityNodeInfo): String {
        val textBuilder = StringBuilder()

        // Extract text from current node
        node.text?.let { textBuilder.append(it).append(" ") }
        node.contentDescription?.let { textBuilder.append(it).append(" ") }

        // Recursively extract from children
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                try {
                    textBuilder.append(extractTextFromNode(child))
                } finally {
                    child.recycle()
                }
            }
        }

        return textBuilder.toString().trim()
    }

    private fun analyzeForScam(text: String, sourceApp: String) {
        serviceScope.launch {
            try {
                val analysis = scamDetector.analyze(text)

                Log.d(TAG, "Analysis result - isScam: ${analysis.isScam}, confidence: ${analysis.confidence}")

                if (analysis.isScam && analysis.confidence >= SCAM_THRESHOLD) {
                    showScamWarning(analysis, sourceApp)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error analyzing text for scam", e)
            }
        }
    }

    private fun showScamWarning(analysis: ScamAnalysis, sourceApp: String) {
        handler.post {
            try {
                Log.w(TAG, "SCAM DETECTED! Confidence: ${analysis.confidence}, Reasons: ${analysis.reasons}")

                // OverlayService 시작
                val intent = Intent(this, OverlayService::class.java).apply {
                    putExtra("confidence", analysis.confidence)
                    putExtra("reasons", analysis.reasons.joinToString(", "))
                    putExtra("sourceApp", sourceApp)
                }
                startService(intent)

            } catch (e: Exception) {
                Log.e(TAG, "Error showing scam warning", e)
            }
        }
    }

    override fun onInterrupt() {
        Log.i(TAG, "Accessibility Service Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        debounceJob?.cancel()
        Log.i(TAG, "Accessibility Service Destroyed")
    }
}
