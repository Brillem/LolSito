package com.example.lolsito.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class UniqueKeysTest {
    @Test
    fun `each base gets its own occurrence ordinal`() {
        val keys = UniqueKeys()
        assertEquals(
            listOf("a#0", "b#0", "a#1", "a#2", "b#1"),
            listOf("a", "b", "a", "a", "b").map(keys::next),
        )
    }

    @Test
    fun `separate instances count independently`() {
        UniqueKeys().next("a")
        assertEquals("a#0", UniqueKeys().next("a"))
    }
}
