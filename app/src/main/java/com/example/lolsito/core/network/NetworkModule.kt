package com.example.lolsito.core.network

import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.Duration

val LolEsportsJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}

object NetworkModule {
    /**
     * Client for the gateway: every call carries the API key and the locale. With an [offlineCache] the
     * answers are also stored on disk and served when the network cannot be reached (see
     * [OfflineFallbackInterceptor]); without one, a call without a network just fails.
     *
     * Explicit timeouts ([CONNECT_TIMEOUT], [READ_TIMEOUT], [CALL_TIMEOUT]) make a dead or very slow network
     * fail soon, so the offline fallback answers quickly instead of after the much longer defaults.
     */
    fun createOkHttpClient(
        builder: OkHttpClient.Builder = OkHttpClient.Builder(),
        offlineCache: OfflineCache? = null,
    ): OkHttpClient {
        builder
            .connectTimeout(CONNECT_TIMEOUT)
            .readTimeout(READ_TIMEOUT)
            .callTimeout(CALL_TIMEOUT)
        builder.addInterceptor(ApiKeyInterceptor())
        if (offlineCache != null) {
            builder.cache(Cache(offlineCache.directory, OFFLINE_CACHE_MAX_BYTES))
            builder.addInterceptor(OfflineFallbackInterceptor(offlineCache.state))
            builder.addNetworkInterceptor(KeepGoodCopiesInterceptor())
        }
        return builder.build()
    }

    private const val OFFLINE_CACHE_MAX_BYTES = 20L * 1024 * 1024

    val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(5)
    val READ_TIMEOUT: Duration = Duration.ofSeconds(10)
    val CALL_TIMEOUT: Duration = Duration.ofSeconds(20)

    fun createApi(baseUrl: String = ApiConfig.BASE_URL, client: OkHttpClient): LolEsportsApi =
        retrofit(baseUrl, client).create(LolEsportsApi::class.java)

    /** [client] must not carry [ApiKeyInterceptor]: the livestats feed takes no key or locale. */
    fun createLiveStatsApi(baseUrl: String = ApiConfig.LIVE_STATS_BASE_URL, client: OkHttpClient): LiveStatsApi =
        retrofit(baseUrl, client).create(LiveStatsApi::class.java)

    private fun retrofit(baseUrl: String, client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(LolEsportsJson.asConverterFactory("application/json".toMediaType()))
            .build()
}
