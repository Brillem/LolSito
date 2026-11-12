package com.example.lolsito.feature.teams.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.ui.LoadStatus
import com.example.lolsito.core.ui.LoadStatusView
import com.example.lolsito.core.ui.TeamLogo
import com.example.lolsito.feature.schedule.domain.ScheduleDay
import com.example.lolsito.feature.schedule.ui.DayHeader
import com.example.lolsito.feature.schedule.ui.EventRow
import com.example.lolsito.feature.schedule.ui.StarIcon
import com.example.lolsito.feature.schedule.ui.StarOutlineIcon
import com.example.lolsito.feature.teams.domain.Player
import com.example.lolsito.feature.teams.domain.RoleGroup
import com.example.lolsito.feature.teams.domain.RosterSplit
import com.example.lolsito.feature.teams.domain.TeamProfile
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

@OptIn(ExperimentalMaterial3Api::class)
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
        TopAppBar(
            title = { Text(state.profile?.name ?: "Team", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            // The app Scaffold already applies the system bar insets.
            windowInsets = WindowInsets(0),
            actions = {
                // Only once the team is known: there is nothing to star before that.
                if (state.profile != null) FavoriteButton(state.isFavorite, onToggleFavorite)
            },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Back" }) {
                    Text("←", style = MaterialTheme.typography.titleLarge)
                }
            },
        )
        LoadStatusView(state.status, "Team not found", onRetry) {
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

@Composable
private fun ProfileHeader(profile: TeamProfile) {
    Column(
        Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TeamLogo(profile.imageUrl, contentDescription = profile.name, size = 88.dp)
        Text(profile.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        val league = listOfNotNull(profile.code, profile.homeLeagueName, profile.homeLeagueRegion).joinToString(" · ")
        Text(league, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

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
        item(key = "role:${group.role}") { RoleTitle(group.role.label) }
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
        LoadStatus.Loading -> item(key = "matches-loading") { Spinner() }
        is LoadStatus.Error -> item(key = "matches-error") {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Couldn't load matches: ${status.message}", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text("Retry") }
            }
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
                    state.olderError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }
                    when {
                        state.isLoadingOlder -> Spinner()
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

@Composable
private fun PlayerRow(player: Player) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        TeamLogo(player.imageUrl, contentDescription = player.summonerName, size = 44.dp, modifier = Modifier.clip(CircleShape))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(player.summonerName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            val realName = listOfNotNull(player.firstName, player.lastName).joinToString(" ")
            if (realName.isNotEmpty()) {
                Text(realName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun RoleTitle(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
    )
}

@Composable
private fun InfoText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun Spinner() {
    Box(Modifier.fillMaxWidth().padding(16.dp).height(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}
