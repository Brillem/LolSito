package com.example.lolsito.core.network

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

val LolEsportsJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}

object NetworkModule {
    /** Client for the gateway: every call carries the API key and the locale. */
    fun createOkHttpClient(builder: OkHttpClient.Builder = OkHttpClient.Builder()): OkHttpClient =
        builder.addInterceptor(ApiKeyInterceptor()).build()

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
