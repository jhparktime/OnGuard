package com.onguard.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Counter Scam 112 API 요청 DTO
 *
 * @param telNum 조회할 전화번호 (하이픈 없이)
 * @param rowCnt 조회할 최대 결과 수
 */
data class CounterScamRequest(
    @SerializedName("telNum")
    val telNum: String,

    @SerializedName("rowCnt")
    val rowCnt: Int = 10
)

/**
 * Counter Scam 112 API 응답 DTO
 *
 * API: https://www.counterscam112.go.kr/main/voiceNumSearchAjax.do
 * 전기통신금융사기 통합대응단 전화번호 조회 API
 *
 * 실제 응답 예시:
 * {
 *   "totCnt": 6,
 *   "voiceCnt": 0,
 *   "smsCnt": 6,
 *   "smsList": [{"dclrCn": "..."}],
 *   "searchData": "최근 3개월 2025.11.06 ~ 2026.02.06"
 * }
 */
data class CounterScamResponse(
    /** 총 신고 건수 */
    @SerializedName("totCnt")
    val totalCount: Int = 0,

    /** 보이스피싱 신고 건수 */
    @SerializedName("voiceCnt")
    val voiceCount: Int = 0,

    /** 스미싱 신고 건수 */
    @SerializedName("smsCnt")
    val smsCount: Int = 0,

    /** 스미싱 상세 목록 */
    @SerializedName("smsList")
    val smsList: List<SmsScamData>? = null,

    /** 검색 기간 정보 (예: "최근 3개월 2025.11.06 ~ 2026.02.06") */
    @SerializedName("searchData")
    val searchData: String? = null
)

/**
 * 스미싱 신고 상세 정보
 */
data class SmsScamData(
    /** 신고 내용 */
    @SerializedName("dclrCn")
    val content: String? = null
)
