package com.onguard.domain.repository

import com.onguard.data.remote.dto.CounterScamResponse

/**
 * Counter Scam 112 전화번호 조회 Repository 인터페이스
 *
 * 전기통신금융사기 통합대응단 API를 통해 전화번호의
 * 보이스피싱/스미싱 신고 이력을 조회합니다.
 *
 * 구현체는 LRU 캐시를 포함하여 중복 API 호출을 방지합니다.
 */
interface CounterScamRepository {

    /**
     * 전화번호의 피싱/스미싱 신고 이력 조회
     *
     * @param phoneNumber 조회할 전화번호 (하이픈 포함/미포함 모두 가능)
     * @return Result<CounterScamResponse> 조회 결과 또는 실패
     *
     * 캐시 전략:
     * - LRU Cache: 100개 항목
     * - TTL: 15분
     * - API 실패 시 빈 결과 반환 (fail-safe)
     */
    suspend fun searchPhone(phoneNumber: String): Result<CounterScamResponse>

    /**
     * 캐시 초기화
     */
    fun clearCache()

    /**
     * 현재 캐시된 항목 수
     */
    fun getCacheSize(): Int
}
