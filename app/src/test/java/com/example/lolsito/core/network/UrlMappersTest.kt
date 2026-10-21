package com.example.lolsito.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlMappersTest {
    @Test
    fun `http urls are rewritten to https`() {
        assertEquals("https://a.com/x.png", "http://a.com/x.png".toHttps())
        assertEquals("https://a.com/x.png", "https://a.com/x.png".toHttps())
        assertNull(null.toHttps())
    }
}
