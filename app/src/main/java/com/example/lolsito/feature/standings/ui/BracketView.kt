package com.example.lolsito.feature.standings.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.lolsito.core.model.Team
import com.example.lolsito.core.model.TeamEmphasis
import com.example.lolsito.core.ui.LOSER_ALPHA
import com.example.lolsito.core.ui.LiveBadge
import com.example.lolsito.core.ui.TeamLogo

/**
 * A bracket drawn from its official layout: one column per API column, each cell under its official title,
 * each match a card, and a line from every match to the match its winner plays next. Drops to a lower
 * bracket get no line; the cell titles tell them apart. Scrolls horizontally when it does not fit.
 */
@Composable
fun BracketView(
    row: StandingsRow.BracketRow,
    onMatchClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    noSpoilers: Boolean = false,
) {
    BracketColumns(row.columns, row.lines, onMatchClick, noSpoilers, modifier)
}

/**
 * Swiss stage: one column per round, scrolling horizontally. Inside a round the matches sit under the record
 * their teams had going in; with [noSpoilers] on, the round lists its matches without records.
 */
@Composable
fun SwissView(
    row: StandingsRow.SwissRow,
    onMatchClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    noSpoilers: Boolean = false,
) {
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(CONNECTOR_WIDTH),
    ) {
        row.rounds.forEach { round ->
            Column(Modifier.width(CARD_WIDTH), verticalArrangement = Arrangement.spacedBy(CARD_GAP)) {
                ColumnTitle(round.name)
                round.displayedGroups(noSpoilers).forEach { group ->
                    if (group.record != null) {
                        Text(
                            group.record,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    group.matches.forEach { match ->
                        BracketMatchCard(match, onClick = { onMatchClick(match.sectionMatch.match.id) }, noSpoilers = noSpoilers, modifier = Modifier)
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.height(TITLE_HEIGHT),
    )
}

/**
 * Columns of match cards placed by [placeBracket], with the cell titles above their cards and the lines
 * behind them. A line leaves a card on its right, runs level to the gap just before the target's column,
 * turns there and enters the target card from its left.
 */
@Composable
private fun BracketColumns(
    columns: List<BracketColumn>,
    lines: List<BracketLine>,
    onMatchClick: (String) -> Unit,
    noSpoilers: Boolean,
    modifier: Modifier = Modifier,
) {
    val slot = CARD_HEIGHT + CARD_GAP
    val step = CARD_WIDTH + CONNECTOR_WIDTH
    val placement = remember(columns, lines) { placeBracket(columns, lines, titleSpace = TITLE_HEIGHT / slot) }
    val width = if (columns.isEmpty()) 0.dp else step * columns.size - CONNECTOR_WIDTH
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Box(Modifier.size(width, slot * placement.height)) {
            Canvas(Modifier.matchParentSize()) {
                val stroke = 1.dp.toPx()
                lines.forEach { line ->
                    val from = placement.cards[line.fromKey] ?: return@forEach
                    val to = placement.cards[line.toKey] ?: return@forEach
                    if (from.column >= to.column) return@forEach
                    val start = Offset((step * from.column + CARD_WIDTH).toPx(), (slot * from.top + CARD_HEIGHT / 2).toPx())
                    val turnX = (step * to.column - CONNECTOR_WIDTH / 2).toPx()
                    val end = Offset((step * to.column).toPx(), (slot * to.top + CARD_HEIGHT / 2).toPx())
                    drawLine(lineColor, start, Offset(turnX, start.y), stroke)
                    drawLine(lineColor, Offset(turnX, start.y), Offset(turnX, end.y), stroke)
                    drawLine(lineColor, Offset(turnX, end.y), end, stroke)
                }
            }
            placement.titles.forEach { title ->
                ColumnTitle(title.name, Modifier.offset(x = step * title.column, y = slot * title.top).width(CARD_WIDTH))
            }
            columns.forEach { column ->
                column.cells.forEach { cell ->
                    cell.matches.forEach { match ->
                        val card = placement.cards[match.key] ?: return@forEach
                        BracketMatchCard(
                            match,
                            onClick = { onMatchClick(match.sectionMatch.match.id) },
                            noSpoilers = noSpoilers,
                            modifier = Modifier.offset(x = step * card.column, y = slot * card.top),
                        )
                    }
                }
            }
        }
    }
}

/** Undecided (TBD) matches are not clickable: see [StandingsRow.MatchRow.isNavigable]. */
@Composable
private fun BracketMatchCard(
    row: StandingsRow.MatchRow,
    onClick: () -> Unit,
    noSpoilers: Boolean,
    modifier: Modifier,
) {
    val teams = row.sectionMatch.match.teams
    val result = row.displayedResult(noSpoilers)
    Box(
        modifier
            .width(CARD_WIDTH)
            .height(CARD_HEIGHT)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (row.isNavigable) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
            if (row.hasBothTeams) {
                BracketTeamLine(teams[0], result.emphasisFor(0), row.displayedGameWins(0, noSpoilers))
                BracketTeamLine(teams[1], result.emphasisFor(1), row.displayedGameWins(1, noSpoilers))
            } else {
                Text("TBD", style = MaterialTheme.typography.bodyMedium)
            }
        }
        // A live match shows no score, so the badge takes the room the game wins would use.
        if (row.isLive) {
            LiveBadge(Modifier.align(Alignment.CenterEnd))
        }
    }
}

@Composable
private fun BracketTeamLine(team: Team, emphasis: TeamEmphasis, gameWins: String?) {
    val weight = if (emphasis == TeamEmphasis.WINNER) FontWeight.ExtraBold else FontWeight.SemiBold
    Row(
        Modifier.fillMaxWidth().alpha(if (emphasis == TeamEmphasis.LOSER) LOSER_ALPHA else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TeamLogo(team.imageUrl, contentDescription = team.name, size = 20.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            team.code,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = weight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (gameWins != null) {
            Text(gameWins, style = MaterialTheme.typography.bodyMedium, fontWeight = weight)
        }
    }
}

private val CARD_WIDTH = 140.dp
private val CARD_HEIGHT = 64.dp
private val CARD_GAP = 12.dp
private val CONNECTOR_WIDTH = 24.dp
private val TITLE_HEIGHT = 24.dp
