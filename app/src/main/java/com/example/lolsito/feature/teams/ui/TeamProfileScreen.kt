package com.example.lolsito.feature.teams.ui

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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.ui.AppCard
import com.example.lolsito.core.ui.AppTopBar
import com.example.lolsito.core.ui.GroupLabel
import com.example.lolsito.core.ui.InfoText
import com.example.lolsito.core.ui.InlineError
import com.example.lolsito.core.ui.LoadStatus
import com.example.lolsito.core.ui.LoadStatusView
import com.example.lolsito.core.ui.MatchCardSkeleton
import com.example.lolsito.core.ui.SectionTitle
import com.example.lolsito.core.ui.SkeletonBlock
import com.example.lolsito.core.ui.SkeletonScreen
import com.example.lolsito.core.ui.Spacing
import com.example.lolsito.core.ui.TeamLogo
import com.example.lolsito.core.ui.rememberTeamAccent
import com.example.lolsito.feature.schedule.domain.ScheduleDay
import com.example.lolsito.feature.schedule.ui.DayHeader
import com.example.lolsito.feature.schedule.ui.EventRow
import com.example.lolsito.feature.schedule.ui.StarIcon
import com.example.lolsito.feature.schedule.ui.StarOutlineIcon
import com.example.lolsito.feature.teams.domain.Player
import com.example.lolsito.feature.teams.domain.PlayerRole
import com.example.lolsito.feature.teams.domain.RoleGroup
import com.example.lolsito.feature.teams.domain.RosterSplit
import com.example.lolsito.feature.teams.domain.TeamProfile
import com.example.lolsito.ui.theme.LabelText
import com.example.lolsito.ui.theme.LolSitoTheme
import java.time.ZoneId

/** Container: wires the ViewModel to the stateless [TeamProfileContent]. */
@Composable
fun TeamProfileScreen(
    viewModel: TeamProfileViewModel,
    onBack: () -> Unit,
    onMatchClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TeamProfileContent(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
        onLoadOlder = viewModel::loadOlder,
        onMatchClick = onMatchClick,
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
                item(key = "header") { ProfileHeader(profile) }
                rosterRows(profile, state.lineup)
                matchRows(state, onRetry, onLoadOlder, onMatchClick)
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
private fun ProfileHeader(profile: TeamProfile) {
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
    }
}

/** How strong the team's color is at the top of the header, as in the match header. */
private const val ACCENT_GLOW_ALPHA = 0.38f

private fun LazyListScope.rosterRows(profile: TeamProfile, lineup: RosterSplit?) {
    if (lineup != null) {
        item(key = "starters") { SectionTitle("Starters") }
        item(key = "starters-caption") { InfoText("Lineup of the last game played") }
        itemsIndexed(lineup.starters, key = { index, _ -> "starter:$index" }) { _, player -> PlayerRow(player) }
        item(key = "roster") { SectionTitle("Rest of the roster") }
        roleGroups(lineup.rest)
        return
    }
    item(key = "roster") { SectionTitle("Roster") }
    if (profile.roster.isEmpty()) {
        item(key = "roster-empty") { InfoText("No players listed") }
    }
    roleGroups(profile.roster)
}

private fun LazyListScope.roleGroups(groups: List<RoleGroup>) {
    groups.forEach { group ->
        item(key = "role:${group.role}") { GroupLabel(group.role.label) }
        itemsIndexed(group.players, key = { index, _ -> "player:${group.role}:$index" }) { _, player -> PlayerRow(player) }
    }
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

/** The card of one player: photo, summoner name and, when known, real name. */
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
