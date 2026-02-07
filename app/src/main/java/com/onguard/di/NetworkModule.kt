package com.onguard.di

import com.onguard.BuildConfig
import com.onguard.data.remote.api.CounterScam112Api
import com.onguard.data.remote.api.ThecheatApi
import com.onguard.data.repository.CounterScamRepositoryImpl
import com.onguard.data.repository.ScamCheckRepositoryImpl
import com.onguard.domain.repository.CounterScamRepository
import com.onguard.domain.repository.ScamCheckRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/** Counter Scam 112 전용 Retrofit Qualifier */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CounterScamRetrofit

/** Counter Scam 112 전용 OkHttpClient Qualifier */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CounterScamOkHttp

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val BASE_URL = "https://apicenter.thecheat.co.kr/"
    private const val TIMEOUT_SECONDS = 30L

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder().apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                })
            }
            connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideThecheatApi(retrofit: Retrofit): ThecheatApi {
        return retrofit.create(ThecheatApi::class.java)
    }

    @Provides
    @Singleton
    fun provideScamCheckRepository(api: ThecheatApi): ScamCheckRepository {
        return ScamCheckRepositoryImpl(api)
    }

    // ========== Counter Scam 112 API ==========

    private const val COUNTER_SCAM_BASE_URL = "https://www.counterscam112.go.kr/"
    private const val COUNTER_SCAM_TIMEOUT_SECONDS = 10L

    /**
     * Counter Scam 112용 세션 쿠키 저장소
     *
     * 서버에서 발급한 JSESSIONID 등의 쿠키를 메모리에 저장하여
     * 후속 API 호출 시 자동으로 전송합니다.
     */
    @Provides
    @Singleton
    @CounterScamOkHttp
    fun provideCounterScamCookieJar(): CookieJar {
        return object : CookieJar {
            private val cookieStore = ConcurrentHashMap<String, List<Cookie>>()

            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                cookieStore[url.host] = cookies
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return cookieStore[url.host] ?: emptyList()
            }
        }
    }

    /**
     * Counter Scam 112 전용 OkHttpClient
     *
     * 세션 쿠키 자동 관리 + AJAX 호출을 위한 특수 헤더 포함:
     * - CookieJar: 세션 쿠키 자동 저장/전송
     * - X-Requested-With: XMLHttpRequest
     * - Referer: 원본 페이지 URL
     * - User-Agent: 모바일 브라우저
     */
    @Provides
    @Singleton
    @CounterScamRetrofit
    fun provideCounterScamOkHttpClient(
        @CounterScamOkHttp cookieJar: CookieJar
    ): OkHttpClient {
        return OkHttpClient.Builder().apply {
            cookieJar(cookieJar)
            connectTimeout(COUNTER_SCAM_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            readTimeout(COUNTER_SCAM_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            writeTimeout(COUNTER_SCAM_TIMEOUT_SECONDS, TimeUnit.SECONDS)

            // AJAX 호출을 위한 헤더 인터셉터
            addInterceptor { chain ->
                val originalRequest = chain.request()
                val requestBuilder = originalRequest.newBuilder()
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; SM-G975F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                    .header("Referer", "https://www.counterscam112.go.kr/phishing/searchPhone.do")
                    .header("Accept", "application/json, text/javascript, */*; q=0.01")
                    .header("Origin", "https://www.counterscam112.go.kr")

                // POST 요청은 JSON, GET 요청은 기본 Content-Type 사용
                if (originalRequest.method == "POST") {
                    requestBuilder.header("Content-Type", "application/json; charset=UTF-8")
                }

                chain.proceed(requestBuilder.build())
            }

            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                })
            }
        }.build()
    }

    @Provides
    @Singleton
    @CounterScamRetrofit
    fun provideCounterScamRetrofit(
        @CounterScamRetrofit okHttpClient: OkHttpClient
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(COUNTER_SCAM_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideCounterScam112Api(
        @CounterScamRetrofit retrofit: Retrofit
    ): CounterScam112Api {
        return retrofit.create(CounterScam112Api::class.java)
    }

    @Provides
    @Singleton
    fun provideCounterScamRepository(api: CounterScam112Api): CounterScamRepository {
        return CounterScamRepositoryImpl(api)
    }
}
