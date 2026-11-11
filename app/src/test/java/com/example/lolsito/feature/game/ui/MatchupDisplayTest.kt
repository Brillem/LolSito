package com.example.lolsito.feature.game.ui

import com.example.lolsito.core.datadragon.championIconUrl
import com.example.lolsito.core.datadragon.itemIconUrl
import com.example.lolsito.core.datadragon.keystoneIconUrl
import com.example.lolsito.feature.game.domain.PlayerDetails
import com.example.lolsito.feature.game.domain.PlayerStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

class MatchupDisplayTest {

    private fun player(id: Int = 1, details: PlayerDetails? = null, champion: String = "Olaf") =
        PlayerStats(id, "P$id", champion, 4, 1, 7, 212, 13, details = details, role = "top")

    private val details = PlayerDetails(
        itemIds = listOf(3047, 3078, 3111),
        primaryStyleId = 8000,
        secondaryStyleId = 8300,
        keystoneId = 8010,
        damageShare = 0.14,
        killParticipation = 0.72,
        totalGold = 10_880,
        wardsPlaced = 14,
        wardsDestroyed = 9,
    )

    // What an icon slot shows

    @Test
    fun `an item slot shows the icon when the version is known, keeping the id for when it fails to load`() {
        assertEquals(
            IconSlotContent.Icon(url = itemIconUrl("16.20.1", 3047)!!, idText = "3047"),
            itemSlotContent("16.20.1", 3047),
        )
    }

    @Test
    fun `an item slot shows the id as text when there is no version`() {
        assertEquals(IconSlotContent.IdText("3047"), itemSlotContent(null, 3047))
    }

    @Test
    fun `an empty item slot shows nothing`() {
        assertEquals(IconSlotContent.Empty, itemSlotContent("16.20.1", 0))
        assertEquals(IconSlotContent.Empty, itemSlotContent(null, 0))
    }

    @Test
    fun `a champion slot shows the portrait when the version is known, keeping the id for when it fails to load`() {
        assertEquals(
            IconSlotContent.Icon(url = championIconUrl("16.20.1", "KSante")!!, idText = "KSante"),
            championSlotContent("16.20.1", "KSante"),
        )
    }

    @Test
    fun `a champion slot shows the champion id as text when there is no version`() {
        assertEquals(IconSlotContent.IdText("MonkeyKing"), championSlotContent(null, "MonkeyKing"))
    }

    @Test
    fun `a champion slot without a champion id shows nothing`() {
        assertEquals(IconSlotContent.Empty, championSlotContent("16.20.1", ""))
        assertEquals(IconSlotContent.Empty, championSlotContent(null, " "))
    }

    // Item strip

    @Test
    fun `an item strip is padded with empty slots to the same length for every player`() {
        assertEquals(listOf(3047, 3078, 3111, 0, 0, 0, 0), itemSlots(details.itemIds))
        assertEquals(List(ITEM_SLOT_COUNT) { 0 }, itemSlots(emptyList()))
        assertEquals(ITEM_SLOT_COUNT, itemSlots(listOf(1)).size)
    }

    @Test
    fun `a full strip keeps its items in slot order and an overlong one is cut to the slot count`() {
        val full = listOf(1, 2, 3, 4, 5, 6, 7)
        assertEquals(full, itemSlots(full))
        assertEquals(full, itemSlots(full + 8))
    }

    @Test
    fun `seven item slots fit in half a 360 dp screen next to the side bar`() {
        assertEquals(152, itemStripWidthDp())
        assertTrue(itemStripWidthDp() + SIDE_BAR_DP <= halfWidthDp(360))
        assertEquals(174, halfWidthDp(360))
    }

    // Which second line a player gets

    @Test
    fun `in the items view a player with details gets the padded item strip`() {
        assertEquals(
            PlayerLine.Items(listOf(3047, 3078, 3111, 0, 0, 0, 0)),
            playerLine(player(details = details), ScoreboardView.ITEMS),
        )
    }

    @Test
    fun `in the stats view a player with details gets compact cells without the keystone`() {
        assertEquals(
            PlayerLine.Stats(
                cells = listOf(StatCell("Gold", "10.9k"), StatCell("KP", "72%"), StatCell("DMG", "14%"), StatCell("Wards", "14/9")),
            ),
            playerLine(player(details = details), ScoreboardView.STATS),
        )
    }

    @Test
    fun `the keystone slot shows the rune icon, keeping the name for when it fails to load`() {
        assertEquals(
            IconSlotContent.Icon(url = keystoneIconUrl(8010)!!, idText = "Conqueror"),
            keystoneSlotContent(details),
        )
    }

    @Test
    fun `the keystone slot shows the number of a rune it does not know`() {
        assertEquals(IconSlotContent.IdText("1234"), keystoneSlotContent(details.copy(keystoneId = 1234)))
    }

    @Test
    fun `the keystone slot shows a dash when the feed sent no keystone and nothing without details`() {
        assertEquals(IconSlotContent.IdText("-"), keystoneSlotContent(details.copy(keystoneId = null)))
        assertEquals(IconSlotContent.Empty, keystoneSlotContent(null))
    }

    @Test
    fun `stats cells round shares and gold the way the comparison does`() {
        val cells = statCells(details.copy(damageShare = 0.286, killParticipation = 1.2, totalGold = 950, wardsPlaced = 0, wardsDestroyed = 0))
        assertEquals(
            listOf(StatCell("Gold", "950"), StatCell("KP", "100%"), StatCell("DMG", "29%"), StatCell("Wards", "0/0")),
            cells,
        )
    }

    @Test
    fun `a player without details has no second line content in either view`() {
        assertEquals(PlayerLine.Unavailable, playerLine(player(), ScoreboardView.ITEMS))
        assertEquals(PlayerLine.Unavailable, playerLine(player(), ScoreboardView.STATS))
    }

    @Test
    fun `the creep score is labeled`() {
        assertEquals("212 CS", csLabel(player()))
    }

    @Test
    fun `the views are named for the toggle`() {
        assertEquals(listOf("Items", "Stats"), ScoreboardView.entries.map { it.label })
        assertEquals(ScoreboardView.ITEMS, ScoreboardView.entries.first())
    }

    // The stale note

    @Test
    fun `the note says when the details are from, in the viewer's time zone`() {
        val since = Instant.parse("2026-11-11T12:34:56Z")

        assertEquals("Details as of 12:34:56", detailsAsOfLabel(since, ZoneOffset.UTC, Locale.GERMANY))
        assertEquals("Details as of 14:34:56", detailsAsOfLabel(since, ZoneOffset.ofHours(2), Locale.GERMANY))
    }
}
