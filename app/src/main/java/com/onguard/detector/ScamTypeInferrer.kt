package com.onguard.detector

import com.onguard.domain.model.ScamType

/**
 * 규칙 기반 탐지 사유 문자열에서 [ScamType]을 추론한다.
 *
 * 순수 함수로 분리하여 단위 테스트가 가능하다.
 */
object ScamTypeInferrer {

    fun inferScamType(reasons: List<String>): ScamType {
        val reasonText = reasons.joinToString(" ")
        return when {
            // 보이스피싱/스미싱 (전화번호 기반) - 가장 먼저 체크
            reasonText.contains("보이스피싱") || reasonText.contains("스미싱") ||
                reasonText.contains("Counter Scam") || reasonText.contains("전화번호") ||
                reasonText.contains("신고 이력") -> ScamType.VOICE_PHISHING

            reasonText.contains("투자") || reasonText.contains("수익") ||
                reasonText.contains("코인") || reasonText.contains("주식") -> ScamType.INVESTMENT

            reasonText.contains("입금") || reasonText.contains("선결제") ||
                reasonText.contains("거래") || reasonText.contains("택배") -> ScamType.USED_TRADE

            reasonText.contains("URL") || reasonText.contains("링크") ||
                reasonText.contains("피싱") -> ScamType.PHISHING

            reasonText.contains("사칭") || reasonText.contains("기관") -> ScamType.IMPERSONATION

            reasonText.contains("대출") -> ScamType.LOAN

            else -> ScamType.UNKNOWN
        }
    }
}
