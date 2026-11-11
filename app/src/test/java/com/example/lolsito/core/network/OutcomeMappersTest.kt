package com.example.lolsito.core.network

import com.example.lolsito.core.model.MatchOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OutcomeMappersTest {
    @Test
    fun `win and loss map to an outcome, anything else to null`() {
        assertEquals(MatchOutcome.WIN, "win".toOutcome())
        assertEquals(MatchOutcome.LOSS, "loss".toOutcome())
        assertNull("tie".toOutcome())
        assertNull(null.toOutcome())
    }
}
