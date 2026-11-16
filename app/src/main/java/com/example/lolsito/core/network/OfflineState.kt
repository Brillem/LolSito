package com.example.lolsito.core.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant

/**
 * Whether the app is showing stored copies because the network is unreachable. Written by
 * [OfflineFallbackInterceptor] from OkHttp's threads and observed by the UI.
 */
class OfflineState {
    private val _cachedSince = MutableStateFlow<Instant?>(null)

    /**
     * Null while the gateway answers. Otherwise, when the oldest stored copy served since the network
     * was last reachable was received.
     */
    val cachedSince: StateFlow<Instant?> = _cachedSince.asStateFlow()

    /** The gateway answered, whatever its status code. */
    fun onNetworkResponse() {
        _cachedSince.value = null
    }

    /** A copy received at [storedAt] was served in place of a request that could not reach the gateway. */
    fun onCachedResponse(storedAt: Instant) {
        _cachedSince.update { current -> if (current == null || storedAt.isBefore(current)) storedAt else current }
    }
}
