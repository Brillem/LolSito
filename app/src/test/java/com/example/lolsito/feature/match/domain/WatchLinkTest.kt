package com.example.lolsito.feature.match.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchLinkTest {
    private val yt = WatchProvider.YOUTUBE
    private val tw = WatchProvider.TWITCH

    @Test
    fun `youtube link builds a watch url`() {
        assertEquals("https://www.youtube.com/watch?v=abc123", WatchLink(yt, "abc123", "en-US").url)
    }

    @Test
    fun `twitch link builds a channel url`() {
        assertEquals("https://www.twitch.tv/lolesports", WatchLink(tw, "lolesports", "en-US").url)
    }

    @Test
    fun `known providers are accepted case-insensitively and unknown ones rejected`() {
        assertEquals(yt, "youtube".toWatchProvider())
        assertEquals(tw, "Twitch".toWatchProvider())
        assertNull("afreeca".toWatchProvider())
        assertNull(null.toWatchProvider())
    }

    @Test
    fun `english link is preferred over earlier ones`() {
        val de = WatchLink(yt, "de", "de-DE")
        val en = WatchLink(yt, "en", "en-US")
        assertEquals(en, pickWatchLink(listOf(de, en)))
    }

    @Test
    fun `any english variant counts as english`() {
        val de = WatchLink(tw, "de", "de-DE")
        val sg = WatchLink(tw, "sg", "en-SG")
        assertEquals(sg, pickWatchLink(listOf(de, sg)))
    }

    @Test
    fun `falls back to the first link when none is english`() {
        val de = WatchLink(yt, "de", "de-DE")
        val es = WatchLink(yt, "es", "es-ES")
        assertEquals(de, pickWatchLink(listOf(de, es)))
    }

    @Test
    fun `a link without locale is the fallback`() {
        val none = WatchLink(yt, "x", null)
        assertEquals(none, pickWatchLink(listOf(none)))
    }

    @Test
    fun `no links yields null`() {
        assertNull(pickWatchLink(emptyList()))
    }
}
