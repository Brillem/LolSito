package com.example.lolsito.feature.standings.ui

import com.example.lolsito.core.network.LolEsportsJson
import com.example.lolsito.core.network.dto.StandingsResponse
import com.example.lolsito.feature.standings.data.toDomainStandings
import com.example.lolsito.testutil.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BracketLayoutTest {

    private val titleSpace = 0.5f

    private fun brackets(fixture: String): List<StandingsRow.BracketRow> =
        buildStandingsRows(LolEsportsJson.decodeFromString<StandingsResponse>(Fixtures.read(fixture)).toDomainStandings())
            .filterIsInstance<StandingsRow.BracketRow>()

    private fun place(row: StandingsRow.BracketRow) = placeBracket(row.columns, row.lines, titleSpace)

    /** Tops of the cards of [cell] in column [column], top to bottom. */
    private fun BracketPlacement.tops(row: StandingsRow.BracketRow, column: Int, cell: Int = 0) =
        row.columns[column].cells[cell].matches.map { cards.getValue(it.key).top }

    @Test
    fun `a single-elimination bracket centers every match between the two it comes from`() {
        val knockouts = brackets("getStandingsV3_worlds2026.json").last()
        val placement = place(knockouts)

        assertEquals(listOf(0.5f, 1.5f, 2.5f, 3.5f), placement.tops(knockouts, 0))
        assertEquals(listOf(1.0f, 3.0f), placement.tops(knockouts, 1))
        assertEquals(listOf(2.0f), placement.tops(knockouts, 2))
        assertEquals(
            listOf(CellTitlePlacement(0, 0f, "Quarterfinals"), CellTitlePlacement(1, 0.5f, "Semifinals"), CellTitlePlacement(2, 1.5f, "Finals")),
            placement.titles,
        )
        assertEquals(4.5f, placement.height)
    }

    @Test
    fun `a double elimination puts the lower bracket below the upper one and the final between them`() {
        val knockouts = brackets("getStandingsV3_msi2026.json").last()
        val placement = place(knockouts)

        assertEquals(listOf(0.5f, 1.5f, 2.5f, 3.5f), placement.tops(knockouts, 0))
        assertEquals(listOf(1.0f, 3.0f), placement.tops(knockouts, 1, cell = 0))
        // Lower round 1 is fed by losers only, so it starts below everything drawn so far, after its title.
        assertEquals(listOf(5.0f, 6.0f), placement.tops(knockouts, 1, cell = 1))
        assertEquals(listOf(2.0f), placement.tops(knockouts, 2, cell = 0))
        assertEquals(listOf(5.0f, 6.0f), placement.tops(knockouts, 2, cell = 1))
        assertEquals(listOf(5.5f), placement.tops(knockouts, 3))
        assertEquals(listOf(5.5f), placement.tops(knockouts, 4))
        assertEquals(listOf(3.75f), placement.tops(knockouts, 5))
        assertTrue(CellTitlePlacement(1, 4.5f, "Lower Bracket - Round 1") in placement.titles)
        assertEquals(7.0f, placement.height)
    }

    @Test
    fun `cards never overlap and every cell title has room above its first card`() {
        val rows = listOf("getStandingsV3_worlds2026.json", "getStandingsV3_lck.json", "getStandingsV3_lec.json", "getStandingsV3_msi2026.json")
            .flatMap(::brackets)
        assertEquals(7, rows.size)

        rows.forEach { row ->
            val placement = place(row)
            assertEquals(row.columns.sumOf { c -> c.cells.sumOf { it.matches.size } }, placement.cards.size)
            row.columns.forEachIndexed { index, column ->
                var previous: Float? = null
                column.cells.forEach { cell ->
                    cell.matches.forEachIndexed { i, match ->
                        val card = placement.cards.getValue(match.key)
                        assertEquals(index, card.column)
                        val room = if (i == 0) 1f + titleSpace else 1f
                        previous?.let { assertTrue("${cell.name} overlaps in ${row.key}", card.top >= it + room - 1e-4f) }
                        if (previous == null) assertTrue(card.top >= titleSpace)
                        previous = card.top
                    }
                }
            }
            assertTrue(placement.titles.all { it.top >= 0f })
            assertEquals(placement.cards.values.maxOf { it.top } + 1f, placement.height)
        }
    }
}
