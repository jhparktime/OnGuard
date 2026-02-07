package com.onguard.data.remote.api

import com.onguard.data.remote.dto.CounterScamRequest
import com.onguard.data.remote.dto.CounterScamResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Counter Scam 112 (전기통신금융사기 통합대응단) API
 *
 * 전화번호의 보이스피싱/스미싱 신고 이력을 조회합니다.
 *
 * Base URL: https://www.counterscam112.go.kr/
 * Endpoint: main/voiceNumSearchAjax.do (JSON)
 *
 * 사용 흐름:
 * 1. initSession() - GET으로 세션 쿠키 획득
 * 2. searchPhone() - POST로 전화번호 조회
 */
interface CounterScam112Api {

    /**
     * 세션 초기화 (페이지 로드하여 세션 쿠키 획득)
     *
     * CookieJar가 자동으로 Set-Cookie 헤더에서 JSESSIONID를 저장합니다.
     *
     * @return Response<ResponseBody> HTML 페이지 (무시)
     */
    @GET("phishing/searchPhone.do")
    suspend fun initSession(): Response<ResponseBody>

    /**
     * 전화번호 피싱/스미싱 신고 조회
     *
     * @param request 조회 요청 (전화번호, 조회 개수)
     * @return CounterScamResponse 조회 결과
     */
    @POST("main/voiceNumSearchAjax.do")
    suspend fun searchPhone(
        @Body request: CounterScamRequest
    ): CounterScamResponse
}
