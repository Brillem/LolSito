package com.example.lolsito.core.network

import com.example.lolsito.core.model.MatchState
import org.junit.Assert.assertEquals
import org.junit.Test

class MatchStateMappersTest {
    @Test
    fun `state strings map to enum with unknown fallback`() {
        assertEquals(MatchState.UNSTARTED, "unstarted".toMatchState())
        assertEquals(MatchState.IN_PROGRESS, "inProgress".toMatchState())
        assertEquals(MatchState.COMPLETED, "completed".toMatchState())
        assertEquals(MatchState.UNKNOWN, "weird".toMatchState())
        assertEquals(MatchState.UNKNOWN, null.toMatchState())
    }
}
