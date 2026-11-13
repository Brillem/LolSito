package com.example.lolsito.feature.standings.ui

/** Where a card sits: its [column] and the [top] of its slot, in slots (one card plus the gap below it). */
data class CardPlacement(val column: Int, val top: Float)

/** A cell title, drawn in [column] right above the first card of its cell; [top] is in slots. */
data class CellTitlePlacement(val column: Int, val top: Float, val name: String)

/** Positions of every card (by row key) and cell title of a bracket, and its [height] in slots. */
data class BracketPlacement(
    val cards: Map<String, CardPlacement>,
    val titles: List<CellTitlePlacement>,
    val height: Float,
)

/**
 * Places the cards of a bracket column by column, each column's cells top to bottom, every cell with
 * [titleSpace] slots above its first card for its title:
 * - a card fed by winners of earlier columns (see [lines]) sits level with the average of their cards, so a
 *   match is centered between the two it comes from and level with a single one;
 * - the first card of a later cell whose matches no winner feeds (such as a lower bracket's first round, fed
 *   by losers) starts below every card placed so far, so that bracket reads as its own block;
 * - any other card goes right below the previous card of its column.
 * A card never moves up past the previous card of its column (plus the title room of a new cell).
 */
fun placeBracket(columns: List<BracketColumn>, lines: List<BracketLine>, titleSpace: Float): BracketPlacement {
    val feeders = lines.groupBy({ it.toKey }, { it.fromKey })
    val cards = mutableMapOf<String, CardPlacement>()
    val titles = mutableListOf<CellTitlePlacement>()
    var bottom = 0f
    columns.forEachIndexed { index, column ->
        var columnBottom = 0f
        column.cells.forEachIndexed { cellIndex, cell ->
            val fedTops = cell.matches.associate { match ->
                match.key to feeders[match.key].orEmpty().mapNotNull { cards[it]?.takeIf { from -> from.column < index }?.top }
            }
            val startsBlock = cellIndex > 0 && fedTops.values.all { it.isEmpty() }
            cell.matches.forEachIndexed { i, match ->
                val lowest = if (i == 0) {
                    (if (startsBlock) maxOf(bottom, columnBottom) else columnBottom) + titleSpace
                } else {
                    columnBottom
                }
                val fed = fedTops.getValue(match.key)
                val top = if (fed.isEmpty()) lowest else maxOf(lowest, fed.average().toFloat())
                if (i == 0) titles += CellTitlePlacement(index, top - titleSpace, cell.name)
                cards[match.key] = CardPlacement(index, top)
                columnBottom = top + 1f
            }
        }
        bottom = maxOf(bottom, columnBottom)
    }
    return BracketPlacement(cards, titles, bottom)
}
