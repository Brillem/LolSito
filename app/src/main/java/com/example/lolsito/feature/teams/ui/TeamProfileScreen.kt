package com.example.lolsito.feature.teams.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.ui.AppCard
import com.example.lolsito.core.ui.AppTopBar
import com.example.lolsito.core.ui.FormMarks
import com.example.lolsito.core.ui.GroupLabel
import com.example.lolsito.core.ui.InfoText
import com.example.lolsito.core.ui.InlineError
import com.example.lolsito.core.ui.LoadStatus
import com.example.lolsito.core.ui.LiveBadge
import com.example.lolsito.core.ui.LoadStatusView
import com.example.lolsito.core.ui.MatchCardSkeleton
import com.example.lolsito.core.ui.SectionTitle
import com.example.lolsito.core.ui.SkeletonBlock
import com.example.lolsito.core.ui.Shapes
import com.example.lolsito.core.ui.SkeletonScreen
import com.example.lolsito.core.ui.Spacing
import com.example.lolsito.core.ui.TeamLogo
import com.example.lolsito.core.ui.WhileStarted
import com.example.lolsito.core.ui.rememberTeamAccent
import com.example.lolsito.feature.schedule.domain.ScheduleDay
import com.example.lolsito.feature.schedule.domain.ScheduleEvent
import com.example.lolsito.feature.schedule.ui.DayHeader
import com.example.lolsito.feature.schedule.ui.EventRow
import com.example.lolsito.feature.schedule.ui.StarIcon
import com.example.lolsito.feature.schedule.ui.countdownLabel
import com.example.lolsito.feature.schedule.ui.StarOutlineIcon
import com.example.lolsito.feature.teams.domain.Player
import com.example.lolsito.feature.teams.domain.RESULT_CHAMPION
import com.example.lolsito.feature.teams.domain.PlayerRole
import com.example.lolsito.feature.teams.domain.RoleGroup
import com.example.lolsito.feature.teams.domain.RosterSplit
import com.example.lolsito.feature.teams.domain.TeamProfile
import com.example.lolsito.feature.teams.domain.opponentOf
import com.example.lolsito.ui.theme.LabelText
import com.example.lolsito.ui.theme.LolSitoTheme
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Container: wires the ViewModel to the stateless [TeamProfileContent]. */
@Composable
fun TeamProfileScreen(
    viewModel: TeamProfileViewModel,
    onBack: () -> Unit,
    onMatchClick: (String) -> Unit,
    onTournamentClick: (leagueId: String, tournamentId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TeamProfileContent(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
        onLoadOlder = viewModel::loadOlder,
        onMatchClick = onMatchClick,
        onTournamentClick = onTournamentClick,
        onToggleFavorite = viewModel::toggleFavorite,
        modifier = modifier,
    )
}

@Composable
fun TeamProfileContent(
    state: TeamProfileUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onLoadOlder: () -> Unit,
    onMatchClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onToggleFavorite: () -> Unit = {},
    onTournamentClick: (leagueId: String, tournamentId: String) -> Unit = { _, _ -> },
) {
    Column(modifier.fillMaxSize()) {
        AppTopBar(
            title = state.profile?.name ?: "Team",
            onBack = onBack,
            actions = {
                // Only once the team is known: there is nothing to star before that.
                if (state.profile != null) FavoriteButton(state.isFavorite, onToggleFavorite)
            },
        )
        LoadStatusView(state.status, "Team not found", onRetry, loading = { ProfileSkeleton() }) {
            val profile = state.profile ?: return@LoadStatusView
            LazyColumn(Modifier.fillMaxSize()) {
                item(key = "header") { ProfileHeader(profile, state.standing) }
                state.nextMatch?.let { next ->
                    item(key = "next-match") {
                        NextMatchCard(next, profile.name, state.zoneId, onClick = next.matchId?.let { id -> { onMatchClick(id) } })
                    }
                }
                rosterRows(profile, state.lineup)
                if (state.form.isNotEmpty()) {
                    item(key = "form-title") { SectionTitle("Recent form") }
                    item(key = "form") {
                        AppCard(Modifier.fillMaxWidth().padding(horizontal = Spacing.Screen, vertical = Spacing.CardGap / 2)) {
                            FormMarks(state.form)
                        }
                    }
                }
                if (state.recentTournaments.isNotEmpty()) {
                    item(key = "tournaments-title") { SectionTitle("Recent tournaments") }
                    itemsIndexed(state.recentTournaments, key = { _, t -> "tournament:${t.tournamentId}" }) { _, tournament ->
                        TournamentRow(tournament, onClick = { onTournamentClick(tournament.leagueId, tournament.tournamentId) })
                    }
                }
                matchRows(state, onRetry, onLoadOlder, onMatchClick)
            }
        }
    }
}

/**
 * A tournament the team played: the league logo, its name and, on the right, how far the team got ("Finalist",
 * "3rd"), in the primary color for the champion. Opens that tournament in the Tournaments tab.
 */
@Composable
private fun TournamentRow(tournament: RecentTournament, onClick: () -> Unit) {
    AppCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Screen, vertical = Spacing.CardGap / 2),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (tournament.leagueImageUrl != null) {
                TeamLogo(tournament.leagueImageUrl, contentDescription = null, size = 32.dp)
                Spacer(Modifier.width(12.dp))
            }
            Text(
                tournament.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (tournament.result != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    tournament.result,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (tournament.result == RESULT_CHAMPION) FontWeight.Bold else null,
                    color = if (tournament.result == RESULT_CHAMPION) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Star in the top bar: filled means the team is a favorite. */
@Composable
private fun FavoriteButton(isFavorite: Boolean, onToggle: () -> Unit) {
    IconButton(
        onClick = onToggle,
        modifier = Modifier.semantics {
            contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites"
            stateDescription = if (isFavorite) "Favorite" else "Not a favorite"
        },
    ) {
        Icon(
            imageVector = if (isFavorite) StarIcon else StarOutlineIcon,
            contentDescription = null,
            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Logo, name and home league over a glow of the team's color, which fades out towards the bottom. */
@Composable
private fun ProfileHeader(profile: TeamProfile, standing: String?) {
    val accent = rememberTeamAccent(profile.imageUrl)
    Column(
        Modifier
            .fillMaxWidth()
            // Scaled by the accent's own alpha, so the glow fades in with it.
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        0f to accent.copy(alpha = accent.alpha * ACCENT_GLOW_ALPHA),
                        1f to Color.Transparent,
                    ),
                )
            }
            .padding(Spacing.Screen),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TeamLogo(profile.imageUrl, contentDescription = profile.name, size = 88.dp)
        Text(profile.name, style = MaterialTheme.typography.headlineSmall)
        val league = listOfNotNull(profile.code, profile.homeLeagueName, profile.homeLeagueRegion).joinToString(" · ")
        Text(league, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // "3rd in LCK · 12-4": absent until known, and for good when the standings give nothing.
        if (standing != null) Text(standing, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** How strong the team's color is at the top of the header, as in the match header. */
private const val ACCENT_GLOW_ALPHA = 0.38f

private val nextDayFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private val nextTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

/**
 * The team's next match, highlighted: "Next match", the opponent, when it starts in [zoneId] and, on the right, the
 * LIVE badge or the countdown in the primary color. The countdown ticks once per minute, on the minute, only while
 * the screen is visible. Opens the match like its row in "Upcoming matches".
 */
@Composable
private fun NextMatchCard(event: ScheduleEvent, teamName: String, zoneId: ZoneId, onClick: (() -> Unit)?) {
    val opponent = opponentOf(event, teamName) ?: return
    val isLive = event.state == MatchState.IN_PROGRESS
    var now by remember { mutableStateOf(Instant.now()) }
    WhileStarted(Unit) {
        while (true) {
            now = Instant.now()
            delay(60_000L - now.toEpochMilli() % 60_000L)
        }
    }
    val countdown = if (isLive) null else countdownLabel(event.startTime, now, event.state)
    val start = event.startTime.atZone(zoneId)
    val meta = listOfNotNull(
        event.leagueName.takeIf { it.isNotBlank() },
        event.blockName,
        event.match?.format?.label,
    ).joinToString(" · ")
    Column {
        SectionTitle("Next match")
        AppCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.Screen, vertical = Spacing.CardGap / 2)
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), Shapes.Card),
            onClick = onClick,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamLogo(opponent.imageUrl, contentDescription = opponent.name, size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "vs ${opponent.name}",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${start.format(nextDayFormatter)} · ${start.format(nextTimeFormatter)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (meta.isNotEmpty()) {
                        Text(
                            meta,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (isLive) {
                    Spacer(Modifier.width(8.dp))
                    LiveBadge()
                } else if (countdown != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        countdown,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/**
 * The roster, each player with their role: the lineup of the last game played first when it is known, then the
 * substitutes; otherwise every player in role order.
 */
private fun LazyListScope.rosterRows(profile: TeamProfile, lineup: RosterSplit?) {
    item(key = "roster") { SectionTitle("Roster") }
    if (lineup != null) {
        playerRows("starter", lineup.starters)
        val substitutes = lineup.rest.flatMap { it.players }
        if (substitutes.isNotEmpty()) {
            item(key = "substitutes") { GroupLabel("Substitutes") }
            playerRows("substitute", substitutes)
        }
        return
    }
    if (profile.roster.isEmpty()) {
        item(key = "roster-empty") { InfoText("No players listed") }
    }
    playerRows("player", profile.roster.flatMap { it.players })
}

private fun LazyListScope.playerRows(keyPrefix: String, players: List<Player>) {
    itemsIndexed(players, key = { index, _ -> "$keyPrefix:$index" }) { _, player -> PlayerRow(player) }
}

private fun LazyListScope.matchRows(
    state: TeamProfileUiState,
    onRetry: () -> Unit,
    onLoadOlder: () -> Unit,
    onMatchClick: (String) -> Unit,
) {
    when (val status = state.matchesStatus) {
        LoadStatus.Loading -> item(key = "matches-loading") { MatchesSkeleton(cards = 2) }
        is LoadStatus.Error -> item(key = "matches-error") {
            InlineError("Couldn't load matches: ${status.message}", onRetry, Modifier.padding(vertical = 8.dp))
        }
        LoadStatus.Empty -> item(key = "matches-empty") { InfoText("No matches found") }
        LoadStatus.Content -> {
            item(key = "upcoming") { SectionTitle("Upcoming matches") }
            if (state.upcomingDays.isEmpty()) item(key = "upcoming-empty") { InfoText("No upcoming matches") }
            dayRows(state.upcomingDays, state.zoneId, state.noSpoilers, onMatchClick)
            item(key = "previous") { SectionTitle("Previous matches") }
            if (state.previousDays.isEmpty()) item(key = "previous-empty") { InfoText("No previous matches loaded yet") }
            dayRows(state.previousDays, state.zoneId, state.noSpoilers, onMatchClick)
            item(key = "load-older") {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    state.olderError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = Spacing.Screen)) }
                    when {
                        state.isLoadingOlder -> MatchesSkeleton(cards = 1)
                        state.canLoadOlder -> TextButton(
                            onClick = onLoadOlder,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.inversePrimary),
                        ) { Text("Load older") }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.dayRows(
    days: List<ScheduleDay>,
    zoneId: ZoneId,
    noSpoilers: Boolean,
    onMatchClick: (String) -> Unit,
) {
    days.forEach { day ->
        item(key = day.headerKey) { DayHeader(day.date) }
        itemsIndexed(day.events, key = { index, _ -> day.keys[index] }) { _, event ->
            EventRow(
                event,
                zoneId,
                // Only upcoming holds live matches: they show their LIVE badge and current score, as in Schedule.
                isLive = event.state == MatchState.IN_PROGRESS,
                noSpoilers = noSpoilers,
                onClick = event.matchId?.let { id -> { onMatchClick(id) } },
            )
        }
    }
}

/** The card of one player: photo, summoner name and, when known, real name; their role on the right. */
@Composable
private fun PlayerRow(player: Player) {
    AppCard(Modifier.fillMaxWidth().padding(horizontal = Spacing.Screen, vertical = Spacing.CardGap / 2)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TeamLogo(player.imageUrl, contentDescription = player.summonerName, size = 44.dp, modifier = Modifier.clip(CircleShape))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(player.summonerName, style = LabelText.TeamCode)
                val realName = listOfNotNull(player.firstName, player.lastName).joinToString(" ")
                if (realName.isNotEmpty()) {
                    Text(realName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (player.role != PlayerRole.OTHER) {
                Spacer(Modifier.width(12.dp))
                Text(player.role.label, style = LabelText.Number, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Matches that are loading inside the list: the silhouette of [cards] match cards, read by a screen
 * reader as a single "Loading". It does not pulse: [SkeletonScreen] fills the screen, so it cannot be a
 * list item.
 */
@Composable
private fun MatchesSkeleton(cards: Int) {
    Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "Loading" }) {
        repeat(cards) { MatchCardSkeleton() }
    }
}

/** The profile while it loads: the header (logo, name, home league) and the first players of the roster. */
@Composable
private fun ProfileSkeleton(players: Int = 5) {
    SkeletonScreen {
        Column {
            Column(
                Modifier.fillMaxWidth().padding(Spacing.Screen),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SkeletonBlock(Modifier.size(88.dp), CircleShape)
                SkeletonBlock(Modifier.size(width = 180.dp, height = 26.dp))
                SkeletonBlock(Modifier.size(width = 130.dp, height = 14.dp))
            }
            SkeletonBlock(Modifier.padding(start = Spacing.Screen, top = 16.dp, bottom = 8.dp).size(width = 120.dp, height = 20.dp))
            repeat(players) {
                AppCard(Modifier.fillMaxWidth().padding(horizontal = Spacing.Screen, vertical = Spacing.CardGap / 2)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SkeletonBlock(Modifier.size(44.dp), CircleShape)
                        Spacer(Modifier.width(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SkeletonBlock(Modifier.size(width = 110.dp, height = 18.dp))
                            SkeletonBlock(Modifier.size(width = 80.dp, height = 12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun TeamProfileContentPreview() {
    LolSitoTheme {
        TeamProfileContent(
            state = TeamProfileUiState(
                status = LoadStatus.Content,
                profile = TeamProfile(
                    id = "1",
                    slug = "t1",
                    name = "T1",
                    code = "T1",
                    imageUrl = null,
                    homeLeagueName = "LCK",
                    homeLeagueRegion = "KOREA",
                    roster = listOf(
                        RoleGroup(PlayerRole.TOP, listOf(Player("1", "Doran", "Hyeonjun", "Choi", null, PlayerRole.TOP))),
                        RoleGroup(PlayerRole.MID, listOf(Player("2", "Faker", "Sanghyeok", "Lee", null, PlayerRole.MID))),
                    ),
                ),
                matchesStatus = LoadStatus.Empty,
                isFavorite = true,
            ),
            onBack = {},
            onRetry = {},
            onLoadOlder = {},
            onMatchClick = {},
        )
    }
}
