package com.example.lolsito.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KnownLeaguesTest {

    @Test
    fun `known league ids resolve to readable names`() {
        assertEquals("LCK", KnownLeagues.nameFor("98767991310872058"))
        assertEquals("Worlds", KnownLeagues.nameFor("98767975604431411"))
    }

    @Test
    fun `unknown league id has no name`() {
        assertNull(KnownLeagues.nameFor("123"))
    }
}
