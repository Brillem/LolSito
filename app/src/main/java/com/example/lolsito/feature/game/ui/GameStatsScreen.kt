package com.example.lolsito.feature.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import com.example.lolsito.core.datadragon.runeName
import com.example.lolsito.core.ui.LoadStatus
import com.example.lolsito.core.ui.LoadStatusView
import com.example.lolsito.core.ui.WhileStarted
import com.example.lolsito.feature.game.domain.GameStats
import com.example.lolsito.feature.game.domain.Matchup
import com.example.lolsito.feature.game.domain.PlayerDetails
import com.example.lolsito.feature.game.domain.PlayerStats
import com.example.lolsito.feature.game.domain.Role
import com.example.lolsito.feature.game.domain.TeamStats
import com.example.lolsito.feature.game.domain.matchups
import java.time.Instant
import java.time.ZoneId

/** Container: wires the ViewModel to the stateless [GameStatsContent]. */
@Composable
fun GameStatsScreen(
    viewModel: GameStatsViewModel,
    teamCodes: Map<String, String>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    WhileStarted(viewModel) { viewModel.pollWhileVisible() }
    GameStatsContent(
        state = state,
        teamCodes = teamCodes,
        onBack = onBack,
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameStatsContent(
    state: GameStatsUiState,
    /** Team codes by team id; a side whose team is not in it is shown by its side name. */
    teamCodes: Map<String, String>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Game stats") },
            // The app Scaffold already applies the system bar insets.
            windowInsets = WindowInsets(0),
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Back" }) {
                    Text("←", style = MaterialTheme.typography.titleLarge)
                }
            },
        )
        LoadStatusView(state.status, emptyMessage = "No stats available for this game", onRetry = onRetry) {
            state.stats?.let { GameStatsBody(it, teamCodes) }
        }
    }
}

/** Code of the team with [teamId] in [teamCodes] (by team id); null when the id is missing, unknown or has a blank code. */
fun teamCodeFor(teamId: String?, teamCodes: Map<String, String>): String? =
    teamId?.let(teamCodes::get)?.takeIf { it.isNotBlank() }

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

private enum class Side(val label: String, val color: Color) {
    BLUE("Blue side", Color(0xFF2F80ED)),
    RED("Red side", Color(0xFFE5484D)),
}

private val teamRows: List<Pair<String, (TeamStats) -> String>> = listOf(
    "Gold" to { team -> formatGold(team.totalGold) },
    "Kills" to { team -> team.kills.toString() },
    "Towers" to { team -> team.towers.toString() },
    "Dragons" to { team -> team.dragons.toString() },
    "Barons" to { team -> team.barons.toString() },
)

@Composable
private fun GameStatsBody(stats: GameStats, teamCodes: Map<String, String>) {
    // The feed says which team plays on which side; event details are not trusted for that.
    val blueCode = teamCodeFor(stats.blueTeamId, teamCodes)
    val redCode = teamCodeFor(stats.redTeamId, teamCodes)
    val matchups = remember(stats.blueTeam.players, stats.redTeam.players) { stats.matchups() }
    // Keys of the expanded matchup rows: by role, so polling refreshes, scrolling and rotation keep them open.
    var expanded by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val open = retainedExpanded(expanded, matchups)
    val toggle = { key: String -> expanded = toggled(open, key) }
    val staleDetailsSince = stats.staleDetailsTime()
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "teams") {
            TeamComparison(stats, blueLabel = teamLabel(blueCode, "Blue"), redLabel = teamLabel(redCode, "Red"))
        }
        item(key = "matchups-header") {
            Column {
                HorizontalDivider()
                MatchupHeader(blueLabel = teamLabel(blueCode, Side.BLUE.label), redLabel = teamLabel(redCode, Side.RED.label))
            }
        }
        items(matchups, key = { it.key }) { matchup ->
            MatchupRow(
                matchup,
                stats.dataDragonVersion,
                staleDetailsSince,
                expanded = matchup.key in open,
                onClick = { toggle(matchup.key) },
            )
        }
    }
}

@Composable
private fun TeamComparison(stats: GameStats, blueLabel: String, redLabel: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ComparisonRow(
            blue = blueLabel,
            label = if (stats.isFinished) "Final" else "LIVE",
            red = redLabel,
            header = true,
        )
        teamRows.forEach { (label, value) ->
            ComparisonRow(blue = value(stats.blueTeam), label = label, red = value(stats.redTeam))
        }
    }
}

@Composable
private fun ComparisonRow(blue: String, label: String, red: String, header: Boolean = false) {
    val valueStyle = if (header) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            blue,
            style = valueStyle,
            fontWeight = FontWeight.Bold,
            color = if (header) Side.BLUE.color else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Text(
            red,
            style = valueStyle,
            fontWeight = FontWeight.Bold,
            color = if (header) Side.RED.color else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MatchupHeader(blueLabel: String, redLabel: String) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
        Text(
            blueLabel,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Side.BLUE.color,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(ROLE_COLUMN_WIDTH))
        Text(
            redLabel,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Side.RED.color,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

private val ROLE_COLUMN_WIDTH = 36.dp
private val STATS_COLUMN_WIDTH = 60.dp

/**
 * Two opposing players on one row, mirrored around the role in the middle: names toward the outer edges,
 * stats toward the center. Opening it shows both players' details side by side.
 */
@Composable
private fun MatchupRow(
    matchup: Matchup,
    version: String?,
    staleDetailsSince: Instant?,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            PlayerHalf(matchup.blue, Side.BLUE, Modifier.weight(1f))
            Text(
                matchup.role?.let(::roleLabel).orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.width(ROLE_COLUMN_WIDTH),
            )
            PlayerHalf(matchup.red, Side.RED, Modifier.weight(1f))
        }
        if (expanded) {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlayerDetailsPanel(matchup.blue, version, staleDetailsSince, Modifier.weight(1f))
                PlayerDetailsPanel(matchup.red, version, staleDetailsSince, Modifier.weight(1f))
            }
        }
        HorizontalDivider()
    }
}

/** Short lane name that fits the narrow center column. */
private fun roleLabel(role: Role): String = when (role) {
    Role.TOP -> "Top"
    Role.JUNGLE -> "Jng"
    Role.MID -> "Mid"
    Role.BOTTOM -> "Bot"
    Role.SUPPORT -> "Sup"
}

/**
 * One side of a matchup: a strip in the side color on the outer edge, the name and champion toward the
 * edge and the stats toward the center. The red side is the mirror image of the blue one.
 */
@Composable
private fun PlayerHalf(player: PlayerStats?, side: Side, modifier: Modifier = Modifier) {
    val mirrored = side == Side.RED
    val horizontalAlignment = if (mirrored) Alignment.End else Alignment.Start
    val textAlign = if (mirrored) TextAlign.End else TextAlign.Start
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (!mirrored) Box(Modifier.width(3.dp).height(36.dp).background(side.color))
        if (player == null) {
            Spacer(Modifier.weight(1f))
        } else {
            val identity: @Composable RowScope.() -> Unit = {
                Column(Modifier.weight(1f).padding(horizontal = 6.dp), horizontalAlignment = horizontalAlignment) {
                    Text(
                        player.summonerName,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = textAlign,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        player.championId,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = textAlign,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            val stats: @Composable RowScope.() -> Unit = {
                Column(Modifier.width(STATS_COLUMN_WIDTH), horizontalAlignment = if (mirrored) Alignment.Start else Alignment.End) {
                    Text(
                        "${player.kills}/${player.deaths}/${player.assists}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Text(
                        "${player.creepScore} CS · ${player.level}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            if (mirrored) {
                stats()
                identity()
            } else {
                identity()
                stats()
            }
        }
        if (mirrored) Box(Modifier.width(3.dp).height(36.dp).background(side.color))
    }
}

/** What opening a matchup shows for one player, as [detailsPanelContent] decides. */
@Composable
private fun PlayerDetailsPanel(player: PlayerStats?, version: String?, staleDetailsSince: Instant?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when (val content = detailsPanelContent(player, staleDetailsSince)) {
            DetailsPanelContent.Unavailable ->
                Text("Details unavailable", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            is DetailsPanelContent.Shown -> {
                val details = content.details
                content.staleSince?.let {
                    Text(
                        detailsAsOfLabel(it, ZoneId.systemDefault()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Three per row: six items do not fit side by side in half a phone width.
                details.itemIds.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { row.forEach { ItemSlot(it, version) } }
                }
                DetailLine("Runes", runesLabel(details))
                DetailLine("Damage share", formatPercent(details.damageShare))
                DetailLine("Kill participation", formatPercent(details.killParticipation))
                DetailLine("Gold earned", formatGold(details.totalGold))
                DetailLine("Wards", "${details.wardsPlaced} placed / ${details.wardsDestroyed} destroyed")
            }
        }
    }
}

/** "Precision / Inspiration - Conqueror"; a part the feed did not send is left out. */
fun runesLabel(details: PlayerDetails): String {
    val trees = listOfNotNull(details.primaryStyleId, details.secondaryStyleId).joinToString(" / ") { runeName(it) }
    val keystone = details.keystoneId?.let { runeName(it) }
    return listOfNotNull(trees.takeIf { it.isNotEmpty() }, keystone).joinToString(" - ").ifEmpty { "-" }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

/** An item icon; the item id as text when there is no icon url or it fails to load, as [itemSlotContent] decides. */
@Composable
private fun ItemSlot(itemId: Int, version: String?) {
    val shape = RoundedCornerShape(4.dp)
    val idText: @Composable (String) -> Unit = { text ->
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
    when (val content = itemSlotContent(version, itemId)) {
        ItemSlotContent.Empty -> Unit
        is ItemSlotContent.IdText -> idText(content.text)
        is ItemSlotContent.Icon -> SubcomposeAsyncImage(
            model = content.url,
            contentDescription = "Item $itemId",
            modifier = Modifier.size(28.dp).clip(shape),
            loading = { Box(Modifier.size(28.dp).background(MaterialTheme.colorScheme.surfaceVariant)) },
            error = { idText(content.idText) },
        )
    }
}

private val previewRoles = listOf("top", "jungle", "mid", "bottom", "support")

private fun previewTeam(firstId: Int, prefix: String, details: PlayerDetails? = null) = TeamStats(
    totalGold = 31850,
    kills = 2,
    towers = 1,
    dragons = 1,
    barons = 0,
    players = (firstId until firstId + 5).map { id ->
        PlayerStats(
            id, "$prefix Player$id", "Olaf", kills = 1, deaths = 2, assists = 3, creepScore = 142, level = 12,
            details = details, role = previewRoles[(id - 1) % 5],
        )
    },
)

private val previewDetails = PlayerDetails(
    itemIds = listOf(3078, 3111, 3071, 6693, 3036, 3026),
    primaryStyleId = 8000,
    secondaryStyleId = 8100,
    keystoneId = 8010,
    damageShare = 0.29,
    killParticipation = 0.65,
    totalGold = 11200,
    wardsPlaced = 12,
    wardsDestroyed = 3,
)

private val previewMatchup = GameStats(
    isFinished = true,
    blueTeam = previewTeam(1, "MVK", previewDetails),
    redTeam = previewTeam(6, "FUR", previewDetails.copy(itemIds = listOf(3078, 3111))),
).matchups().first()

@Preview(showBackground = true)
@Composable
private fun MatchupRowPreview() {
    MatchupRow(previewMatchup, version = null, staleDetailsSince = null, expanded = false, onClick = {})
}

@Preview(showBackground = true)
@Composable
private fun MatchupRowExpandedPreview() {
    MatchupRow(previewMatchup, version = null, staleDetailsSince = null, expanded = true, onClick = {})
}

@Preview(showBackground = true)
@Composable
private fun GameStatsContentPreview() {
    GameStatsContent(
        state = GameStatsUiState(
            LoadStatus.Content,
            GameStats(
                isFinished = true,
                blueTeam = previewTeam(1, "MVK"),
                redTeam = previewTeam(6, "FUR"),
                blueTeamId = "mvk",
                redTeamId = "fur",
            ),
        ),
        teamCodes = mapOf("mvk" to "MVK", "fur" to "FUR"),
        onBack = {},
        onRetry = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun GameStatsEmptyPreview() {
    GameStatsContent(
        state = GameStatsUiState(LoadStatus.Empty),
        teamCodes = emptyMap(),
        onBack = {},
        onRetry = {},
    )
}
