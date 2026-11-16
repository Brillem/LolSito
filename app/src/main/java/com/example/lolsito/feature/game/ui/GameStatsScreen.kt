package com.example.lolsito.feature.game.ui

import com.example.lolsito.ui.theme.WinGreen
import com.example.lolsito.feature.game.domain.winnerSide
import com.example.lolsito.feature.game.domain.MapSide
import com.example.lolsito.core.ui.LOSER_ALPHA
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import com.example.lolsito.core.ui.AppCard
import com.example.lolsito.core.ui.AppTopBar
import com.example.lolsito.core.ui.LoadStatus
import com.example.lolsito.core.ui.LoadStatusView
import com.example.lolsito.core.ui.Shapes
import com.example.lolsito.core.ui.SkeletonBlock
import com.example.lolsito.core.ui.SkeletonScreen
import com.example.lolsito.core.ui.Spacing
import com.example.lolsito.core.ui.TeamLogo
import com.example.lolsito.core.ui.WhileStarted
import com.example.lolsito.feature.game.domain.DragonType
import com.example.lolsito.feature.game.domain.GameStats
import com.example.lolsito.feature.game.domain.GameTeam
import com.example.lolsito.feature.game.domain.GoldHistory
import com.example.lolsito.feature.game.domain.GoldPoint
import com.example.lolsito.feature.game.domain.Matchup
import com.example.lolsito.feature.game.domain.PlayerDetails
import com.example.lolsito.feature.game.domain.PlayerHealth
import com.example.lolsito.feature.game.domain.PlayerStats
import com.example.lolsito.feature.game.domain.TeamStats
import com.example.lolsito.feature.game.domain.matchups
import com.example.lolsito.ui.theme.DragonChemtech
import com.example.lolsito.ui.theme.DragonCloud
import com.example.lolsito.ui.theme.DragonElder
import com.example.lolsito.ui.theme.DragonHextech
import com.example.lolsito.ui.theme.DragonInfernal
import com.example.lolsito.ui.theme.DragonMountain
import com.example.lolsito.ui.theme.DragonOcean
import com.example.lolsito.ui.theme.HealthGreen
import com.example.lolsito.ui.theme.LabelText
import com.example.lolsito.ui.theme.LolSitoTheme
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Container: wires the ViewModel to the stateless [GameStatsContent]. */
@Composable
fun GameStatsScreen(
    viewModel: GameStatsViewModel,
    teams: Map<String, GameTeam>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    WhileStarted(viewModel) { viewModel.pollWhileVisible() }
    GameStatsContent(
        state = state,
        teams = teams,
        onBack = onBack,
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}

@Composable
fun GameStatsContent(
    state: GameStatsUiState,
    /** Teams by team id; a side whose team is not in it is shown by its side name. */
    teams: Map<String, GameTeam>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Kept here, above the load status, so rotation and a retry keep the chosen view.
    var view by rememberSaveable { mutableStateOf(ScoreboardView.ITEMS) }
    Column(modifier.fillMaxSize()) {
        AppTopBar("Game stats", onBack)
        LoadStatusView(
            state.status,
            emptyMessage = "No stats available for this game",
            onRetry = onRetry,
            loading = { GameStatsSkeleton() },
        ) {
            state.stats?.let { GameStatsBody(it, state.goldHistory, teams, view, onViewChange = { view = it }) }
        }
    }
}

/** The team with [teamId] in [teams] (by team id); null when the id is missing, unknown or the team has a blank code. */
fun teamFor(teamId: String?, teams: Map<String, GameTeam>): GameTeam? =
    teamId?.let(teams::get)?.takeIf { it.code.isNotBlank() }
        ?.let { team -> team.copy(logoUrl = team.logoUrl?.takeIf { it.isNotBlank() }) }

/** The team [code] when known, otherwise [fallback] (the side). */
fun teamLabel(code: String?, fallback: String): String = code?.takeIf { it.isNotBlank() } ?: fallback

/** A 0 to 1 share as a whole percent ("29%"); values outside that range are clamped. */
fun formatPercent(share: Double): String {
    val clamped = if (share.isNaN()) 0.0 else share.coerceIn(0.0, 1.0)
    return "${Math.round(clamped * 100)}%"
}

/** Gold as broadcasts show it: from a thousand up, in thousands rounded to one decimal ("31.9k"). */
fun formatGold(gold: Int): String {
    if (gold < 1000) return gold.toString()
    val tenths = (gold + 50) / 100
    return "${tenths / 10}.${tenths % 10}k"
}

// The two sides of the map, as broadcasts color them. They are not the app accent nor team colors.
private val BlueSide = Color(0xFF2F80ED)
private val RedSide = Color(0xFFE5484D)

private enum class Side(val label: String, val color: Color) {
    BLUE("Blue side", BlueSide),
    RED("Red side", RedSide),
}

// The small styles of the scoreboard, in the label typeface: it is condensed, so a row of two players
// fits a narrow phone at sizes that can still be read.

/** A player name and the K/D/A beside it. */
private val ScoreboardText = LabelText.Number.copy(fontSize = 14.sp, lineHeight = 16.sp)

/** The creep score over the K/D/A, and the cells of the stats view. */
private val ScoreboardCaption = LabelText.Number.copy(fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium)

/** The level over a portrait. */
private val LevelText = LabelText.Number.copy(fontSize = 11.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold)

/** The text standing for a keystone or a champion whose image is missing. */
private val SlotText = LabelText.Number.copy(fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Medium)

/** The id standing for an item whose icon is missing; it has to fit a slot of [ITEM_SLOT_DP]. */
private val ItemIdText = LabelText.Number.copy(fontSize = 9.sp, lineHeight = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp)

// The scoreboard is one card made of blocks: the header, rounded on top, then a block per matchup, the
// last one rounded at the bottom. A thin gap of background separates them.
private val ScoreboardTop = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
private val ScoreboardBottom = RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)
private val SCOREBOARD_ROW_GAP = 2.dp

/** The team totals shown as plain numbers; the dragons have their own row, [DragonsRow], after the towers. */
private val teamRowsBeforeDragons: List<Pair<String, (TeamStats) -> String>> = listOf(
    "Gold" to { team -> formatGold(team.totalGold) },
    "Kills" to { team -> team.kills.toString() },
    "Towers" to { team -> team.towers.toString() },
)

private val teamRowsAfterDragons: List<Pair<String, (TeamStats) -> String>> = listOf(
    "Barons" to { team -> team.barons.toString() },
)

@Composable
private fun GameStatsBody(
    stats: GameStats,
    goldHistory: GoldHistory?,
    teams: Map<String, GameTeam>,
    view: ScoreboardView,
    onViewChange: (ScoreboardView) -> Unit,
) {
    // The feed says which team plays on which side; event details are not trusted for that.
    val blue = teamFor(stats.blueTeamId, teams)
    val red = teamFor(stats.redTeamId, teams)
    val matchups = remember(stats.blueTeam.players, stats.redTeam.players) { stats.matchups() }
    val staleDetailsSince = stats.staleDetailsTime()
    val showHealth = stats.showsHealth()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = Spacing.CardGap / 2)) {
        item(key = "teams") {
            TeamComparison(stats, blue = blue, red = red)
        }
        // Secondary: without a history of at least two points the screen is as it was without the chart.
        goldHistory?.points?.takeIf { it.size >= 2 }?.let { points ->
            item(key = "gold-difference") {
                GoldDifferenceChart(points, blueColor = Side.BLUE.color, redColor = Side.RED.color)
            }
        }
        item(key = "matchups-header") {
            MatchupHeader(blue = blue, red = red, alone = matchups.isEmpty())
        }
        itemsIndexed(matchups, key = { _, matchup -> matchup.key }) { index, matchup ->
            MatchupRow(matchup, stats.dataDragonVersion, view, showHealth, last = index == matchups.lastIndex)
        }
        item(key = "view-toggle") {
            Column {
                ViewToggle(view, onViewChange)
                // Once for the whole list: the details of every player come from the same frame.
                staleDetailsSince?.let {
                    Text(
                        detailsAsOfLabel(it, ZoneId.systemDefault()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Spacing.Screen, end = Spacing.Screen, bottom = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ViewToggle(view: ScoreboardView, onViewChange: (ScoreboardView) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Spacing.Screen, vertical = 8.dp)) {
        ScoreboardView.entries.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == view,
                onClick = { onViewChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index, ScoreboardView.entries.size),
                label = { Text(option.label) },
            )
        }
    }
}

/** A team code with its small logo beside it, on the outer edge; the red side is the mirror image of the blue one. */
@Composable
private fun TeamTag(
    label: String,
    logoUrl: String?,
    mirrored: Boolean,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    /** How the game ended for this team; null while it is being played or when the winner is unknown. */
    won: Boolean? = null,
) {
    Row(
        modifier.alpha(if (won == false) LOSER_ALPHA else 1f),
        horizontalArrangement = if (mirrored) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!mirrored && logoUrl != null) {
            TeamLogo(logoUrl, contentDescription = null, size = TEAM_LOGO_SIZE)
            Spacer(Modifier.width(6.dp))
        }
        if (mirrored && won == true) {
            WinBadge()
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label,
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (!mirrored && won == true) {
            Spacer(Modifier.width(6.dp))
            WinBadge()
        }
        if (mirrored && logoUrl != null) {
            Spacer(Modifier.width(6.dp))
            TeamLogo(logoUrl, contentDescription = null, size = TEAM_LOGO_SIZE)
        }
    }
}

/** Marks the team that won the game, in the green the app uses for a win. */
@Composable
private fun WinBadge() {
    Text(
        "WIN",
        style = LabelText.Overline,
        fontWeight = FontWeight.Bold,
        color = Color.Black,
        modifier = Modifier.background(WinGreen, Shapes.Block).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

private val TEAM_LOGO_SIZE = 20.dp

/** The card comparing the two teams: who they are and the state of the game, then their totals row by row. */
@Composable
private fun TeamComparison(stats: GameStats, blue: GameTeam?, red: GameTeam?, modifier: Modifier = Modifier) {
    AppCard(modifier.fillMaxWidth().padding(horizontal = Spacing.Screen, vertical = Spacing.CardGap / 2)) {
        // The feed does not say who won: see winnerSide for how it is told, and when it is not.
        val winner = stats.winnerSide()
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TeamTag(
                    teamLabel(blue?.code, "Blue"), blue?.logoUrl, mirrored = false, color = Side.BLUE.color,
                    style = LabelText.TeamCode, modifier = Modifier.weight(1f),
                    won = winner?.let { it == MapSide.BLUE },
                )
                Text(
                    if (stats.isFinished) "Final" else "LIVE",
                    style = LabelText.Overline,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                TeamTag(
                    teamLabel(red?.code, "Red"), red?.logoUrl, mirrored = true, color = Side.RED.color,
                    style = LabelText.TeamCode, modifier = Modifier.weight(1f),
                    won = winner?.let { it == MapSide.RED },
                )
            }
            teamRowsBeforeDragons.forEach { (label, value) ->
                ComparisonRow(blue = value(stats.blueTeam), label = label, red = value(stats.redTeam))
            }
            DragonsRow(blue = stats.blueTeam, red = stats.redTeam)
            teamRowsAfterDragons.forEach { (label, value) ->
                ComparisonRow(blue = value(stats.blueTeam), label = label, red = value(stats.redTeam))
            }
        }
    }
}

/**
 * The dragons each team took, one icon per dragon in the order taken. The middle label takes
 * only the room it needs, so each side has more of it than in a [ComparisonRow].
 */
@Composable
private fun DragonsRow(blue: TeamStats, red: TeamStats) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        DragonLabels(blue, mirrored = false, Modifier.weight(1f))
        Text(
            "Dragons",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        DragonLabels(red, mirrored = true, Modifier.weight(1f))
    }
}

/** A team's dragons as icons; the count instead when the feed gave no types for them (none taken shows 0). */
@Composable
private fun DragonLabels(team: TeamStats, mirrored: Boolean, modifier: Modifier = Modifier) {
    val textAlign = if (mirrored) TextAlign.End else TextAlign.Start
    if (team.dragonTypes.isEmpty()) {
        Text(
            team.dragons.toString(),
            style = LabelText.Number,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = textAlign,
            modifier = modifier,
        )
        return
    }
    val unknownColor = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = dragonsDescription(team.dragonTypes) },
        horizontalArrangement = Arrangement.spacedBy(4.dp, if (mirrored) Alignment.End else Alignment.Start),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        team.dragonTypes.forEach { type -> DragonIcon(type, dragonColor(type) ?: unknownColor) }
    }
}

/** The game's icon of a dragon type; its short colored label when the icon cannot be loaded. */
@Composable
private fun DragonIcon(type: DragonType, labelColor: Color) {
    SubcomposeAsyncImage(
        model = dragonIconUrl(type),
        contentDescription = null,
        modifier = Modifier.size(DRAGON_ICON_SIZE),
        loading = {},
        error = {
            Text(
                dragonLabel(type),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = labelColor,
                maxLines = 1,
                softWrap = false,
            )
        },
    )
}

private val DRAGON_ICON_SIZE = 22.dp

/** The label color of a dragon type; null for an unknown one, which uses a muted color. */
private fun dragonColor(type: DragonType): Color? = when (type) {
    DragonType.INFERNAL -> DragonInfernal
    DragonType.OCEAN -> DragonOcean
    DragonType.CLOUD -> DragonCloud
    DragonType.MOUNTAIN -> DragonMountain
    DragonType.HEXTECH -> DragonHextech
    DragonType.CHEMTECH -> DragonChemtech
    DragonType.ELDER -> DragonElder
    DragonType.UNKNOWN -> null
}

@Composable
private fun ComparisonRow(blue: String, label: String, red: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            blue,
            style = LabelText.Number,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Text(
            red,
            style = LabelText.Number,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

/** The top block of the scoreboard: each team over its column of players. [alone] when no matchup follows it. */
@Composable
private fun MatchupHeader(blue: GameTeam?, red: GameTeam?, alone: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = SCREEN_PADDING_DP.dp, end = SCREEN_PADDING_DP.dp, top = Spacing.CardGap / 2)
            .clip(if (alone) Shapes.Card else ScoreboardTop)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TeamTag(
            teamLabel(blue?.code, Side.BLUE.label), blue?.logoUrl, mirrored = false, color = Side.BLUE.color,
            style = LabelText.TeamCode, modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(HALF_GAP_DP.dp))
        TeamTag(
            teamLabel(red?.code, Side.RED.label), red?.logoUrl, mirrored = true, color = Side.RED.color,
            style = LabelText.TeamCode, modifier = Modifier.weight(1f),
        )
    }
}

// The first line of a half is as tall as the champion portrait; the second line depends on the view. In a live
// game a thin health bar sits between the two. Every row of a view has the same height whatever it shows, so
// the two halves and all rows line up.
private val PORTRAIT_SIZE = 36.dp
private val LINE_GAP = 2.dp
private val STATS_LINE_HEIGHT = 14.dp
private val KDA_WIDTH = 50.dp
private val KEYSTONE_SIZE = 18.dp
private val HALF_PADDING = 2.dp
private val HEALTH_BAR_HEIGHT = 3.dp

private fun secondLineHeight(view: ScoreboardView) = when (view) {
    ScoreboardView.ITEMS -> ITEM_SLOT_DP.dp
    ScoreboardView.STATS -> STATS_LINE_HEIGHT * 2 + LINE_GAP
}

/**
 * Two opposing players on one row, mirrored around the gap in the middle: portraits and names toward the
 * outer edges, creep score and K/D/A toward the center, and the same second line on both sides. The row is
 * a block of the scoreboard card; the [last] one closes the card with rounded corners.
 */
@Composable
private fun MatchupRow(matchup: Matchup, version: String?, view: ScoreboardView, showHealth: Boolean, last: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = SCREEN_PADDING_DP.dp, end = SCREEN_PADDING_DP.dp, top = SCOREBOARD_ROW_GAP)
            .clip(if (last) ScoreboardBottom else RectangleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerHalf(matchup.blue, Side.BLUE, version, view, showHealth, Modifier.weight(1f))
        Spacer(Modifier.width(HALF_GAP_DP.dp))
        PlayerHalf(matchup.red, Side.RED, version, view, showHealth, Modifier.weight(1f))
    }
}

/**
 * One side of a matchup: a strip in the side color on the outer edge, then the headline, the health bar when
 * [showHealth] and the second line.
 */
@Composable
private fun PlayerHalf(
    player: PlayerStats?,
    side: Side,
    version: String?,
    view: ScoreboardView,
    showHealth: Boolean,
    modifier: Modifier = Modifier,
) {
    val mirrored = side == Side.RED
    val healthHeight = if (showHealth) HEALTH_BAR_HEIGHT + LINE_GAP else 0.dp
    val height = PORTRAIT_SIZE + healthHeight + LINE_GAP + secondLineHeight(view)
    Row(modifier.height(height)) {
        if (!mirrored) Box(Modifier.width(SIDE_BAR_DP.dp).height(height).background(side.color))
        Column(Modifier.weight(1f).padding(horizontal = HALF_PADDING)) {
            if (player != null) {
                PlayerHeadline(player, player.details, version, mirrored)
                if (showHealth) {
                    Spacer(Modifier.height(LINE_GAP))
                    HealthBar(player.health, mirrored)
                }
                Spacer(Modifier.height(LINE_GAP))
                SecondLine(player, version, view, mirrored)
            }
        }
        if (mirrored) Box(Modifier.width(SIDE_BAR_DP.dp).height(height).background(side.color))
    }
}

/**
 * A thin bar filled by the share of health the player has left, from the outer edge, without numbers. A player
 * the feed sent no health for keeps the bar's place empty, so every row stays as tall as the others.
 */
@Composable
private fun HealthBar(health: PlayerHealth?, mirrored: Boolean) {
    val shape = RoundedCornerShape(HEALTH_BAR_HEIGHT / 2)
    if (health == null) {
        Spacer(Modifier.fillMaxWidth().height(HEALTH_BAR_HEIGHT))
        return
    }
    Box(
        Modifier.fillMaxWidth().height(HEALTH_BAR_HEIGHT).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = if (mirrored) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth(health.fraction).height(HEALTH_BAR_HEIGHT).background(HealthGreen))
    }
}

/** Portrait (with the level) on the outer edge, name over the keystone, creep score over K/D/A toward the center. */
@Composable
private fun PlayerHeadline(player: PlayerStats, details: PlayerDetails?, version: String?, mirrored: Boolean) {
    val textAlign = if (mirrored) TextAlign.End else TextAlign.Start
    val portrait: @Composable () -> Unit = {
        Box(Modifier.size(PORTRAIT_SIZE)) {
            ChampionPortrait(player.championId, version)
            LevelBadge(player.level, Modifier.align(if (mirrored) Alignment.BottomEnd else Alignment.BottomStart))
        }
    }
    val identity: @Composable (Modifier) -> Unit = { identityModifier ->
        Column(identityModifier.padding(horizontal = 4.dp), horizontalAlignment = if (mirrored) Alignment.End else Alignment.Start) {
            Text(
                player.summonerName,
                style = ScoreboardText,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = textAlign,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            KeystoneSlot(details, mirrored)
        }
    }
    val score: @Composable () -> Unit = {
        // Toward the center, away from the name.
        val scoreAlign = if (mirrored) TextAlign.Start else TextAlign.End
        Column(Modifier.width(KDA_WIDTH), horizontalAlignment = if (mirrored) Alignment.Start else Alignment.End) {
            Text(
                csLabel(player),
                style = ScoreboardCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = scoreAlign,
                maxLines = 1,
                softWrap = false,
            )
            Text(
                "${player.kills}/${player.deaths}/${player.assists}",
                style = ScoreboardText,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = scoreAlign,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
    Row(Modifier.fillMaxWidth().height(PORTRAIT_SIZE), verticalAlignment = Alignment.CenterVertically) {
        if (mirrored) {
            score()
            identity(Modifier.weight(1f))
            portrait()
        } else {
            portrait()
            identity(Modifier.weight(1f))
            score()
        }
    }
}

/** The level on a dark rounded background, so it reads over any portrait. */
@Composable
private fun LevelBadge(level: Int, modifier: Modifier = Modifier) {
    Text(
        level.toString(),
        style = LevelText,
        color = Color.White,
        maxLines = 1,
        softWrap = false,
        modifier = modifier.background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp)).padding(horizontal = 3.dp),
    )
}

/**
 * The keystone icon under the name; its name when the icon fails to load, its number when there is no icon,
 * and an empty slot without details, as [keystoneSlotContent] decides. The slot has the same height in every case.
 */
@Composable
private fun KeystoneSlot(details: PlayerDetails?, mirrored: Boolean) {
    val text: @Composable (String) -> Unit = { text ->
        Text(
            text,
            style = SlotText,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Box(
        Modifier.fillMaxWidth().height(KEYSTONE_SIZE),
        contentAlignment = if (mirrored) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        when (val content = keystoneSlotContent(details)) {
            IconSlotContent.Empty -> Unit
            is IconSlotContent.IdText -> text(content.text)
            is IconSlotContent.Icon -> SubcomposeAsyncImage(
                model = content.url,
                contentDescription = content.idText,
                modifier = Modifier.size(KEYSTONE_SIZE),
                loading = {},
                error = { text(content.idText) },
            )
        }
    }
}

/** The champion portrait; the champion id as small text when there is no portrait or it fails to load, as [championSlotContent] decides. */
@Composable
private fun ChampionPortrait(championId: String, version: String?) {
    val shape = RoundedCornerShape(6.dp)
    val placeholder: @Composable (String) -> Unit = { text ->
        Box(
            Modifier.size(PORTRAIT_SIZE).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (text.isNotEmpty()) {
                Text(
                    text,
                    style = SlotText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    when (val content = championSlotContent(version, championId)) {
        IconSlotContent.Empty -> placeholder("")
        is IconSlotContent.IdText -> placeholder(content.text)
        is IconSlotContent.Icon -> SubcomposeAsyncImage(
            model = content.url,
            contentDescription = championId,
            modifier = Modifier.size(PORTRAIT_SIZE).clip(shape),
            loading = { placeholder("") },
            error = { placeholder(content.idText) },
        )
    }
}

/** What [playerLine] decides for the player: the item strip, the stat cells, or a muted note. */
@Composable
private fun SecondLine(player: PlayerStats, version: String?, view: ScoreboardView, mirrored: Boolean) {
    Box(
        Modifier.fillMaxWidth().height(secondLineHeight(view)),
        contentAlignment = if (mirrored) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        when (val line = playerLine(player, view)) {
            PlayerLine.Unavailable -> Text(
                "Details unavailable",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
            )
            // The red strip is the mirror image of the blue one: its first item is on the outer edge.
            is PlayerLine.Items -> Row(horizontalArrangement = Arrangement.spacedBy(ITEM_GAP_DP.dp)) {
                (if (mirrored) line.slots.asReversed() else line.slots).forEach { ItemSlot(it, version) }
            }
            is PlayerLine.Stats -> StatsGrid(line)
        }
    }
}

@Composable
private fun StatsGrid(line: PlayerLine.Stats) {
    val (gold, kp, damage, wards) = line.cells
    Column(verticalArrangement = Arrangement.spacedBy(LINE_GAP)) {
        Row {
            StatCellText(gold, Modifier.weight(1f))
            StatCellText(kp, Modifier.weight(1f))
        }
        Row {
            StatCellText(damage, Modifier.weight(1f))
            StatCellText(wards, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCellText(cell: StatCell, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val text: AnnotatedString = buildAnnotatedString {
        withStyle(SpanStyle(color = muted)) { append(cell.label) }
        append(' ')
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(cell.value) }
    }
    Text(
        text,
        style = ScoreboardCaption,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** An item icon; the item id as text when there is no icon url or it fails to load, as [itemSlotContent] decides. */
@Composable
private fun ItemSlot(itemId: Int, version: String?) {
    val shape = RoundedCornerShape(3.dp)
    val size = ITEM_SLOT_DP.dp
    val idText: @Composable (String) -> Unit = { text ->
        Box(Modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Text(
                text,
                style = ItemIdText,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
    when (val content = itemSlotContent(version, itemId)) {
        // Reserves its place so every strip lines up.
        IconSlotContent.Empty -> Spacer(Modifier.size(size))
        is IconSlotContent.IdText -> idText(content.text)
        is IconSlotContent.Icon -> SubcomposeAsyncImage(
            model = content.url,
            contentDescription = "Item $itemId",
            modifier = Modifier.size(size).clip(shape),
            loading = { Box(Modifier.size(size).background(MaterialTheme.colorScheme.surfaceVariant)) },
            error = { idText(content.idText) },
        )
    }
}

/** Loading state shaped like the screen: the comparison card, then the scoreboard header and its five rows. */
@Composable
private fun GameStatsSkeleton() {
    val rowHeight = PORTRAIT_SIZE + LINE_GAP + ITEM_SLOT_DP.dp + 10.dp
    SkeletonScreen {
        Column(Modifier.fillMaxWidth().padding(vertical = Spacing.CardGap / 2)) {
            SkeletonBlock(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Screen, vertical = Spacing.CardGap / 2)
                    .height(190.dp),
                Shapes.Card,
            )
            SkeletonBlock(
                Modifier
                    .fillMaxWidth()
                    .padding(start = SCREEN_PADDING_DP.dp, end = SCREEN_PADDING_DP.dp, top = Spacing.CardGap / 2)
                    .height(38.dp),
                ScoreboardTop,
            )
            repeat(5) { index ->
                SkeletonBlock(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = SCREEN_PADDING_DP.dp, end = SCREEN_PADDING_DP.dp, top = SCOREBOARD_ROW_GAP)
                        .height(rowHeight),
                    if (index == 4) ScoreboardBottom else RectangleShape,
                )
            }
        }
    }
}

private val previewRoles = listOf("top", "jungle", "mid", "bottom", "support")
private val previewChampions = listOf("KSante", "Viego", "Azir", "Jinx", "Nautilus")

private val previewDragons = listOf(DragonType.CLOUD, DragonType.HEXTECH, DragonType.UNKNOWN)

private fun previewTeam(
    firstId: Int,
    prefix: String,
    details: PlayerDetails? = null,
    nameSuffix: String = "",
    health: PlayerHealth? = null,
) = TeamStats(
    totalGold = 31850,
    kills = 12,
    towers = 7,
    dragons = 3,
    barons = 1,
    players = (firstId until firstId + 5).map { id ->
        PlayerStats(
            id, "$prefix Player$id$nameSuffix", previewChampions[(id - 1) % 5], kills = 10, deaths = 12, assists = 11,
            creepScore = 342, level = 18, details = details, role = previewRoles[(id - 1) % 5],
            health = health?.let { it.copy(current = it.max * (id % 5) / 4) },
        )
    },
    dragonTypes = previewDragons,
)

private val previewDetails = PlayerDetails(
    itemIds = listOf(3078, 3111, 3071, 6693, 3036, 3026, 3340),
    primaryStyleId = 8400,
    secondaryStyleId = 8300,
    keystoneId = 8437,
    damageShare = 0.14,
    killParticipation = 0.72,
    totalGold = 10880,
    wardsPlaced = 14,
    wardsDestroyed = 9,
)

private fun previewStats(blueDetails: PlayerDetails?, redDetails: PlayerDetails?, nameSuffix: String = "") = GameStats(
    isFinished = true,
    blueTeam = previewTeam(1, "BLG", blueDetails, nameSuffix),
    redTeam = previewTeam(6, "T1", redDetails, nameSuffix),
    blueTeamId = "blg",
    redTeamId = "t1",
)

private val previewTeams = mapOf("blg" to GameTeam("BLG"), "t1" to GameTeam("T1"))

private val previewGoldHistory = GoldHistory(
    start = Instant.EPOCH,
    points = listOf(0, -77, -243, 93, -591, -1235, -677, -1209, -1613, -1216, -1112, -1455, 419, -1808, -418, -27, -614, 906, 4848)
        .mapIndexed { index, difference -> GoldPoint(Duration.ofMinutes(2L * index), difference) },
)

@Composable
private fun ScoreboardPreview(stats: GameStats, view: ScoreboardView) {
    LolSitoTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) {
            GameStatsBody(stats, previewGoldHistory, previewTeams, view, onViewChange = {})
        }
    }
}

private const val PREVIEW_BACKGROUND = 0xFF000000

@Preview(showBackground = true, backgroundColor = PREVIEW_BACKGROUND, widthDp = 360, heightDp = 780)
@Composable
private fun ItemsViewPreview() =
    ScoreboardPreview(previewStats(previewDetails, previewDetails.copy(itemIds = previewDetails.itemIds.take(4))), ScoreboardView.ITEMS)

@Preview(showBackground = true, backgroundColor = PREVIEW_BACKGROUND, widthDp = 360, heightDp = 780)
@Composable
private fun LiveHealthPreview() {
    val health = PlayerHealth(current = 2000, max = 2000)
    val live = GameStats(
        isFinished = false,
        blueTeam = previewTeam(1, "BLG", previewDetails, health = health),
        redTeam = previewTeam(6, "T1", previewDetails, health = health),
        blueTeamId = "blg",
        redTeamId = "t1",
    )
    ScoreboardPreview(live, ScoreboardView.ITEMS)
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BACKGROUND, widthDp = 360, heightDp = 780)
@Composable
private fun StatsViewPreview() = ScoreboardPreview(previewStats(previewDetails, previewDetails), ScoreboardView.STATS)

@Preview(showBackground = true, backgroundColor = PREVIEW_BACKGROUND, widthDp = 360, heightDp = 780)
@Composable
private fun DetailsUnavailablePreview() = ScoreboardPreview(previewStats(previewDetails, null), ScoreboardView.ITEMS)

@Preview(showBackground = true, backgroundColor = PREVIEW_BACKGROUND, widthDp = 360, heightDp = 780)
@Composable
private fun LongNamesPreview() =
    ScoreboardPreview(previewStats(previewDetails, previewDetails, nameSuffix = " The Very Long Named"), ScoreboardView.STATS)

@Preview(showBackground = true, backgroundColor = PREVIEW_BACKGROUND, widthDp = 360)
@Composable
private fun GameStatsEmptyPreview() {
    LolSitoTheme {
        GameStatsContent(state = GameStatsUiState(LoadStatus.Empty), teams = emptyMap(), onBack = {}, onRetry = {})
    }
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BACKGROUND, widthDp = 360, heightDp = 780)
@Composable
private fun GameStatsLoadingPreview() {
    LolSitoTheme {
        GameStatsContent(state = GameStatsUiState(LoadStatus.Loading), teams = emptyMap(), onBack = {}, onRetry = {})
    }
}
