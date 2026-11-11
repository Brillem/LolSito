package com.example.lolsito.feature.game.ui

import com.example.lolsito.core.datadragon.itemIconUrl
import com.example.lolsito.feature.game.domain.GameStats
import com.example.lolsito.feature.game.domain.Matchup
import com.example.lolsito.feature.game.domain.PlayerDetails
import com.example.lolsito.feature.game.domain.PlayerStats
import com.example.lolsito.feature.game.domain.TeamStats
import com.example.lolsito.feature.game.domain.matchups
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

class MatchupDisplayTest {

    private val roles = listOf("top", "jungle", "mid", "bottom", "support")

    private fun player(id: Int, role: String?, kills: Int = 0, details: PlayerDetails? = null) =
        PlayerStats(id, "P$id", "Olaf", kills, 0, 0, 0, 1, details = details, role = role)

    private fun game(kills: Int = 0, blueRoles: List<String?> = roles, redRoles: List<String?> = roles) = GameStats(
        isFinished = false,
        blueTeam = TeamStats(0, 0, 0, 0, 0, blueRoles.mapIndexed { i, r -> player(1 + i, r, kills) }),
        redTeam = TeamStats(0, 0, 0, 0, 0, redRoles.mapIndexed { i, r -> player(6 + i, r, kills) }),
    )

    private val details = PlayerDetails(listOf(3047), 8000, 8300, 8010, 0.25, 0.5, 5000, 1, 2)

    // Which rows are open

    @Test
    fun `tapping a closed row opens it and tapping an open row closes it`() {
        assertEquals(listOf("role:TOP"), toggled(emptyList(), "role:TOP"))
        assertEquals(emptyList<String>(), toggled(listOf("role:TOP"), "role:TOP"))
    }

    @Test
    fun `tapping a row leaves the other open rows as they were`() {
        assertEquals(listOf("role:TOP", "role:MID"), toggled(listOf("role:TOP"), "role:MID"))
        assertEquals(listOf("role:TOP"), toggled(listOf("role:TOP", "role:MID"), "role:MID"))
    }

    @Test
    fun `rows keep their key, so open rows stay open when a refresh changes the stats`() {
        val open = listOf("role:JUNGLE", "role:SUPPORT")

        val afterRefresh = game(kills = 7).matchups()

        assertEquals(open, retainedExpanded(open, game(kills = 0).matchups()))
        assertEquals(open, retainedExpanded(open, afterRefresh))
    }

    @Test
    fun `open rows that are no longer in the list drop out`() {
        val oneLaneShort = game(blueRoles = roles - "mid", redRoles = roles - "mid").matchups()

        assertEquals(listOf("role:TOP"), retainedExpanded(listOf("role:TOP", "role:MID", "gone"), oneLaneShort))
    }

    @Test
    fun `a closed row that vanished and came back is not open again`() {
        val without = game(blueRoles = roles - "mid", redRoles = roles - "mid").matchups()
        val open = toggled(retainedExpanded(listOf("role:MID"), without), "role:TOP")

        assertEquals(listOf("role:TOP"), open)
        assertEquals(listOf("role:TOP"), retainedExpanded(open, game().matchups()))
    }

    @Test
    fun `without known roles rows are keyed by slot and each keeps its key`() {
        val unknown = game(blueRoles = List(5) { null }, redRoles = List(5) { null }).matchups()

        assertEquals(listOf("slot:0", "slot:1", "slot:2", "slot:3", "slot:4"), unknown.map(Matchup::key))
        assertEquals(listOf("slot:1"), retainedExpanded(listOf("slot:1", "role:TOP"), unknown))
    }

    // What an item slot shows

    @Test
    fun `an item slot shows the icon when the version is known, keeping the id for when it fails to load`() {
        assertEquals(
            ItemSlotContent.Icon(url = itemIconUrl("16.20.1", 3047)!!, idText = "3047"),
            itemSlotContent("16.20.1", 3047),
        )
    }

    @Test
    fun `an item slot shows the id as text when there is no version`() {
        assertEquals(ItemSlotContent.IdText("3047"), itemSlotContent(null, 3047))
    }

    @Test
    fun `an empty item slot shows nothing`() {
        assertEquals(ItemSlotContent.Empty, itemSlotContent("16.20.1", 0))
        assertEquals(ItemSlotContent.Empty, itemSlotContent(null, 0))
    }

    // What the panel of a player shows

    @Test
    fun `a player with details shows them, without a note when they are current`() {
        assertEquals(DetailsPanelContent.Shown(details, staleSince = null), detailsPanelContent(player(1, "top", details = details), null))
    }

    @Test
    fun `details kept from an earlier frame are shown with the time they are from`() {
        val since = Instant.parse("2026-11-11T12:34:56Z")

        assertEquals(DetailsPanelContent.Shown(details, since), detailsPanelContent(player(1, "top", details = details), since))
    }

    @Test
    fun `a player without details, or nobody, shows details unavailable`() {
        val since = Instant.parse("2026-11-11T12:34:56Z")

        assertEquals(DetailsPanelContent.Unavailable, detailsPanelContent(player(1, "top"), null))
        assertEquals(DetailsPanelContent.Unavailable, detailsPanelContent(player(1, "top"), since))
        assertEquals(DetailsPanelContent.Unavailable, detailsPanelContent(null, null))
    }

    @Test
    fun `the note says when the details are from, in the viewer's time zone`() {
        val since = Instant.parse("2026-11-11T12:34:56Z")

        assertEquals("Details as of 12:34:56", detailsAsOfLabel(since, ZoneOffset.UTC, Locale.GERMANY))
        assertEquals("Details as of 14:34:56", detailsAsOfLabel(since, ZoneOffset.ofHours(2), Locale.GERMANY))
    }
}
