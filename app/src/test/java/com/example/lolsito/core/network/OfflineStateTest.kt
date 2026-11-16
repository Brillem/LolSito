package com.example.lolsito.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class OfflineStateTest {

    private val state = OfflineState()
    private val noon = Instant.parse("2026-11-14T12:00:00Z")

    @Test
    fun `starts online`() {
        assertNull(state.cachedSince.value)
    }

    @Test
    fun `keeps the time of the oldest copy served while offline`() {
        state.onCachedResponse(noon)
        state.onCachedResponse(noon.minusSeconds(600))
        state.onCachedResponse(noon.plusSeconds(600))

        assertEquals(noon.minusSeconds(600), state.cachedSince.value)
    }

    @Test
    fun `a network answer ends the offline state and forgets its time`() {
        state.onCachedResponse(noon.minusSeconds(600))

        state.onNetworkResponse()
        assertNull(state.cachedSince.value)

        state.onCachedResponse(noon)
        assertEquals(noon, state.cachedSince.value)
    }
}
