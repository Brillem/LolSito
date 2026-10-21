package com.example.lolsito.core.network

import okhttp3.Interceptor
import okhttp3.Response

/** Adds the API key header and the locale query parameter required by every call. */
class ApiKeyInterceptor(
    private val apiKey: String = ApiConfig.API_KEY,
    private val locale: String = ApiConfig.LOCALE,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val url = original.url.newBuilder().setQueryParameter("hl", locale).build()
        val request = original.newBuilder()
            .url(url)
            .header("x-api-key", apiKey)
            .build()
        return chain.proceed(request)
    }
}
