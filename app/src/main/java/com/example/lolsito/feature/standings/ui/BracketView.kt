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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.lolsito.core.model.Team
import com.example.lolsito.core.model.TeamEmphasis
import com.example.lolsito.core.ui.LOSER_ALPHA
import com.example.lolsito.core.ui.LiveBadge
import com.example.lolsito.core.ui.TeamLogo

/**
 * Single-elimination bracket: one column per round, each match a card, and lines joining every
 * pair of matches to the one they feed. Scrolls horizontally when it does not fit.
 */
@Composable
fun BracketView(
    row: StandingsRow.BracketRow,
    onMatchClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    noSpoilers: Boolean = false,
) {
    BracketColumns(row.rounds, onMatchClick, noSpoilers, modifier)
}

/** Double elimination: the upper bracket, the lower bracket and the grand final, each with its own columns. */
@Composable
fun DoubleEliminationView(
    row: StandingsRow.DoubleEliminationRow,
    onMatchClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    noSpoilers: Boolean = false,
) {
    Column(modifier.fillMaxWidth()) {
        BracketPartTitle("Upper bracket")
        BracketColumns(row.upper, onMatchClick, noSpoilers)
        BracketPartTitle("Lower bracket")
        BracketColumns(row.lower, onMatchClick, noSpoilers)
        BracketColumns(listOf(row.grandFinal), onMatchClick, noSpoilers)
    }
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
private fun BracketPartTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
    )
}

@Composable
private fun ColumnTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        modifier = Modifier.height(TITLE_HEIGHT),
    )
}

/**
 * Columns of match cards with connector lines. The tallest column sets the height and every column splits it
 * evenly, so a match sits centered between the matches that feed it. Lines are drawn only between columns
 * whose link is known: an elbow from each pair when the next column halves, a straight line when it keeps its
 * size.
 */
@Composable
private fun BracketColumns(
    columns: List<BracketColumn>,
    onMatchClick: (String) -> Unit,
    noSpoilers: Boolean,
    modifier: Modifier = Modifier,
) {
    val firstSlot = CARD_HEIGHT + CARD_GAP
    val height = firstSlot * (columns.maxOfOrNull { it.matches.size } ?: 0)
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        columns.forEachIndexed { index, column ->
            val slot = height / column.matches.size.coerceAtLeast(1)
            Column(Modifier.width(CARD_WIDTH)) {
                ColumnTitle(column.name)
                Box(Modifier.height(height)) {
                    column.matches.forEachIndexed { matchIndex, match ->
                        BracketMatchCard(
                            match,
                            onClick = { onMatchClick(match.sectionMatch.match.id) },
                            noSpoilers = noSpoilers,
                            modifier = Modifier.offset(y = slot * matchIndex + (slot - CARD_HEIGHT) / 2),
                        )
                    }
                }
            }
            val next = columns.getOrNull(index + 1)
            if (next != null) {
                val connectorModifier = Modifier.padding(top = TITLE_HEIGHT).size(CONNECTOR_WIDTH, height)
                when {
                    !column.linkedToNext -> Spacer(connectorModifier)
                    next.matches.size * 2 == column.matches.size ->
                        BracketConnectors(feeders = column.matches.size, slot = slot, color = lineColor, modifier = connectorModifier)
                    next.matches.size == column.matches.size ->
                        StraightConnectors(count = column.matches.size, slot = slot, color = lineColor, modifier = connectorModifier)
                    else -> Spacer(connectorModifier)
                }
            }
        }
    }
}

/** Elbow lines from each pair of feeder matches (2k, 2k+1) to the next-round match k. */
@Composable
private fun BracketConnectors(feeders: Int, slot: Dp, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val slotPx = slot.toPx()
        val stroke = 1.dp.toPx()
        val midX = size.width / 2
        for (pair in 0 until feeders / 2) {
            val top = slotPx * (2 * pair + 0.5f)
            val bottom = slotPx * (2 * pair + 1.5f)
            val target = (top + bottom) / 2
            drawLine(color, Offset(0f, top), Offset(midX, top), stroke)
            drawLine(color, Offset(0f, bottom), Offset(midX, bottom), stroke)
            drawLine(color, Offset(midX, top), Offset(midX, bottom), stroke)
            drawLine(color, Offset(midX, target), Offset(size.width, target), stroke)
        }
    }
}

/** A straight line from each match k to the next-round match k, for rounds of the same size. */
@Composable
private fun StraightConnectors(count: Int, slot: Dp, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val slotPx = slot.toPx()
        val stroke = 1.dp.toPx()
        for (k in 0 until count) {
            val y = slotPx * (k + 0.5f)
            drawLine(color, Offset(0f, y), Offset(size.width, y), stroke)
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
