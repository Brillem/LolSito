package com.example.lolsito.feature.standings.ui

import com.example.lolsito.feature.standings.domain.Tournament
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TournamentLabelTest {
    @Test
    fun `slug words are capitalized`() {
        assertEquals("Worlds 2026", tournament("worlds_2026").label)
        assertEquals("First Stand 2026", tournament("first_stand_2026").label)
    }

    @Test
    fun `league acronyms are upper-cased`() {
        assertEquals("LCK Split 3 2026", tournament("lck_split_3_2026").label)
        assertEquals("LCK Split 2 2026", tournament("lck-split_2_2026").label)
        assertEquals("MSI 2025", tournament("msi_2025").label)
        assertEquals("LTA North Split 1 2026", tournament("lta_north_split_1_2026").label)
        assertEquals("CBLOL Split 2 2026", tournament("CbLol_split_2_2026").label)
        assertEquals("EMEA Masters 2026", tournament("emea_masters_2026").label)
    }

    @Test
    fun `acronyms are only matched as whole words`() {
        assertEquals("Lcks 2026", formatTournamentSlug("lcks_2026"))
    }

    @Test
    fun `blank slug falls back to the id`() {
        assertEquals("123", tournament(" ").label)
    }

    private fun tournament(slug: String, start: String? = null, end: String? = null) =
        Tournament("123", slug, start?.let(LocalDate::parse), end?.let(LocalDate::parse))
}
