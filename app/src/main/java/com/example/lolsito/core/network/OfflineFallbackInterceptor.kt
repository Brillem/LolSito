package com.example.lolsito.core.network

import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.time.Instant

/** Where the gateway answers are stored on disk and the [state] that tells when a stored one is served. */
class OfflineCache(val directory: File, val state: OfflineState)

/**
 * Network first, stored copy as a fallback. Every request goes to the network, also while its stored
 * copy is still fresh, so a refresh always brings new data. Only when the network cannot be reached
 * (an [IOException], never an HTTP error status) the request is repeated against the client's cache
 * alone, however old the copy is; without a copy the original failure is thrown as before.
 *
 * A request marked `Cache-Control: no-store` (live data) is neither stored nor answered from a copy.
 *
 * Needs a client with a [okhttp3.Cache]; see [NetworkModule.createOkHttpClient].
 */
class OfflineFallbackInterceptor(private val state: OfflineState) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val live = request.cacheControl.noStore
        val networkRequest = request.newBuilder()
            .cacheControl(if (live) NETWORK_UNSTORED else CacheControl.FORCE_NETWORK)
            .build()
        val response = try {
            chain.proceed(networkRequest)
        } catch (failure: IOException) {
            // A cancelled call also fails with an IOException, and says nothing about the network.
            if (live || chain.call().isCanceled()) throw failure
            return storedCopy(chain, request) ?: throw failure
        }
        state.onNetworkResponse()
        return response
    }

    /** The copy of [request] in the cache, marking the state offline; null when there is none. */
    private fun storedCopy(chain: Interceptor.Chain, request: Request): Response? {
        val copy = try {
            chain.proceed(request.newBuilder().cacheControl(CacheControl.FORCE_CACHE).build())
        } catch (_: IOException) {
            return null
        }
        // Without a copy OkHttp answers a synthetic 504 that has no cache response behind it.
        if (copy.cacheResponse == null) {
            copy.close()
            return null
        }
        state.onCachedResponse(Instant.ofEpochMilli(copy.receivedResponseAtMillis))
        return copy
    }

    private companion object {
        val NETWORK_UNSTORED: CacheControl = CacheControl.Builder().noCache().noStore().build()
    }
}

/**
 * Network interceptor that keeps error answers out of the cache. OkHttp stores some error statuses by
 * default (404, 410 and others), and one of them would replace the last good copy of its request, which
 * is the one to show without a network. The caller still gets the error as the gateway sent it.
 */
class KeepGoodCopiesInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.code < HTTP_BAD_REQUEST) return response
        return response.newBuilder().header("Cache-Control", "no-store").build()
    }

    private companion object {
        const val HTTP_BAD_REQUEST = 400
    }
}
