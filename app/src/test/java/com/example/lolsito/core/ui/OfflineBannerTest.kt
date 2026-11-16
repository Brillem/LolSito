package com.example.lolsito.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class OfflineBannerTest {

    private val madrid = ZoneId.of("Europe/Madrid")
    private val cachedSince = Instant.parse("2026-11-14T13:32:00Z")

    @Test
    fun `data stored today shows its local time`() {
        val label = offlineBannerLabel(cachedSince, madrid, today = LocalDate.of(2026, 11, 14), locale = Locale.UK)

        assertEquals("Offline · showing data from 14:32", label)
    }

    @Test
    fun `data stored on another day shows its date too`() {
        val label = offlineBannerLabel(cachedSince, madrid, today = LocalDate.of(2026, 11, 16), locale = Locale.UK)

        assertTrue(label, label.startsWith("Offline · showing data from "))
        assertTrue(label, label.contains("14 Nov 2026"))
        assertTrue(label, label.endsWith("14:32"))
    }

    @Test
    fun `the day is the one in the given zone`() {
        val lateEvening = Instant.parse("2026-11-14T23:30:00Z")

        val label = offlineBannerLabel(lateEvening, madrid, today = LocalDate.of(2026, 11, 15), locale = Locale.UK)

        assertEquals("Offline · showing data from 00:30", label)
    }
}
