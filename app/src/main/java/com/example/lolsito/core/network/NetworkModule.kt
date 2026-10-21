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
    fun createOkHttpClient(builder: OkHttpClient.Builder = OkHttpClient.Builder()): OkHttpClient =
        builder.addInterceptor(ApiKeyInterceptor()).build()

    fun createApi(baseUrl: String = ApiConfig.BASE_URL, client: OkHttpClient): LolEsportsApi =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(LolEsportsJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(LolEsportsApi::class.java)
}
