package com.example.lolsito.feature.teams.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lolsito.core.model.League
import com.example.lolsito.core.preferences.FavoriteTeam
import com.example.lolsito.core.ui.AppCard
import com.example.lolsito.core.ui.DropdownPicker
import com.example.lolsito.core.ui.InfoText
import com.example.lolsito.core.ui.InlineError
import com.example.lolsito.core.ui.LoadStatus
import com.example.lolsito.core.ui.LoadStatusView
import com.example.lolsito.core.ui.SearchField
import com.example.lolsito.core.ui.SectionTitle
import com.example.lolsito.core.ui.Shapes
import com.example.lolsito.core.ui.SkeletonBlock
import com.example.lolsito.core.ui.SkeletonScreen
import com.example.lolsito.core.ui.Spacing
import com.example.lolsito.core.ui.TeamLogo
import com.example.lolsito.core.ui.rememberTeamAccent
import com.example.lolsito.feature.teams.domain.DirectoryTeam
import com.example.lolsito.feature.teams.domain.PlayerHit
import com.example.lolsito.feature.teams.domain.TeamSearchResults
import com.example.lolsito.feature.teams.domain.TeamSummary
import com.example.lolsito.ui.theme.LabelText
import com.example.lolsito.ui.theme.LolSitoTheme

/**
 * Container: wires the ViewModel to the stateless [TeamsContent]. [onTeamClick] receives the team id and the
 * selected league id; [onFavoriteClick] only the team id, since a favorite belongs to no selected league.
 */
@Composable
fun TeamsScreen(
    viewModel: TeamsViewModel,
    onTeamClick: (teamId: String, leagueId: String) -> Unit,
    onFavoriteClick: (teamId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Favorites are starred in a team's profile: read them again whenever this tab comes back.
    // System notifications are turned on and off outside the app: check them again as well.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshFavorites()
        viewModel.refreshNotificationsNotice()
    }
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onNotificationPermissionResult(granted)
    }
    val onToggleReminders = {
        when {
            state.remindersEnabled -> viewModel.disableReminders()
            // Android 13+ needs the permission to notify; the system asks only when it is not granted yet.
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED ->
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> viewModel.enableReminders()
        }
    }
    TeamsContent(
        state = state,
        onSelectLeague = viewModel::selectLeague,
        onRetry = viewModel::retry,
        onRetryLeagues = viewModel::retryLeagues,
        onTeamClick = { teamId -> onTeamClick(teamId, state.selectedLeagueId) },
        onFavoriteClick = onFavoriteClick,
        onToggleReminders = onToggleReminders,
        onQueryChange = viewModel::onQueryChange,
        onRetrySearch = viewModel::retrySearch,
        modifier = modifier,
    )
    // Going back while searching clears the search first.
    BackHandler(enabled = state.isSearching) { viewModel.clearQuery() }
}

@Composable
fun TeamsContent(
    state: TeamsUiState,
    onSelectLeague: (String) -> Unit,
    onRetry: () -> Unit,
    onRetryLeagues: () -> Unit,
    onTeamClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onFavoriteClick: (String) -> Unit = {},
    onToggleReminders: () -> Unit = {},
    onQueryChange: (String) -> Unit = {},
    onRetrySearch: () -> Unit = {},
) {
    Column(modifier.fillMaxSize()) {
        SearchField(
            value = state.query,
            onValueChange = onQueryChange,
            onClear = { onQueryChange("") },
            placeholder = "Search teams and players",
            modifier = Modifier.padding(horizontal = Spacing.Screen, vertical = 8.dp),
        )
        if (state.isSearching) {
            // A result belongs to no selected league, so it opens like a favorite: by team id only.
            SearchResultsView(state, onRetrySearch, onResultClick = onFavoriteClick)
            return@Column
        }
        if (state.favorites.isNotEmpty()) {
            FavoritesRow(state.favorites, state.remindersEnabled, state.remindersNotice, onFavoriteClick, onToggleReminders)
        }
        DropdownPicker(
            label = state.selectedLeagueName,
            items = state.leagues,
            itemLabel = { league -> league.name },
            onSelect = { league -> onSelectLeague(league.id) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Screen, vertical = 8.dp),
            imageUrl = state.leagues.firstOrNull { it.id == state.selectedLeagueId }?.imageUrl,
            itemImageUrl = { league -> league.imageUrl },
            title = "League",
            isSelected = { league -> league.id == state.selectedLeagueId },
        )
        if (state.leaguesStatus is LoadStatus.Error) {
            InlineError("Couldn't load leagues", onRetryLeagues)
        }
        LoadStatusView(state.status, "No teams for this league right now", onRetry, loading = { TeamListSkeleton() }) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.teams, key = { it.id }) { team -> TeamRow(team, onClick = { onTeamClick(team.id) }) }
            }
        }
    }
}

/** What replaces the tab's content while the user searches: the team list loading, failing, or the results. */
@Composable
private fun SearchResultsView(state: TeamsUiState, onRetry: () -> Unit, onResultClick: (String) -> Unit) {
    LoadStatusView(state.searchStatus, "", onRetry, loading = { TeamListSkeleton() }) {
        val results = state.searchResults
        when {
            state.queryTooShort -> InfoText("Type at least 2 characters")
            results.isEmpty -> InfoText("No teams or players found")
            else -> LazyColumn(Modifier.fillMaxSize()) {
                if (results.teams.isNotEmpty()) {
                    item(key = "teams-title") { SectionTitle("Teams") }
                    items(results.teams, key = { "team-${it.id}" }) { team ->
                        SearchTeamRow(team, onClick = { onResultClick(team.id) })
                    }
                }
                if (results.players.isNotEmpty()) {
                    item(key = "players-title") { SectionTitle("Players") }
                    items(results.players, key = { "player-${it.team.id}-${it.player.id ?: it.player.summonerName}" }) { hit ->
                        SearchPlayerRow(hit, onClick = { onResultClick(hit.team.id) })
                    }
                }
            }
        }
    }
}

/** A team result: logo, name, then its code and home league. */
@Composable
private fun SearchTeamRow(team: DirectoryTeam, onClick: () -> Unit) {
    AppCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Screen, vertical = Spacing.CardGap / 2),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TeamLogo(team.imageUrl, contentDescription = null, size = 32.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(team.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(team.code, team.homeLeagueName).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A player result: summoner name, real name and role, and the team's code and logo at the end. */
@Composable
private fun SearchPlayerRow(hit: PlayerHit, onClick: () -> Unit) {
    val player = hit.player
    val realName = listOfNotNull(player.firstName, player.lastName).joinToString(" ")
    AppCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Screen, vertical = Spacing.CardGap / 2),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(player.summonerName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(realName, player.role.label).filter { it.isNotEmpty() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(hit.team.code, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            Spacer(Modifier.width(8.dp))
            TeamLogo(hit.team.imageUrl, contentDescription = hit.team.name, size = 28.dp)
        }
    }
}

/**
 * The favorites above the league picker, one compact card each, outlined in the team's color; tapping
 * one opens its profile. The bell next to the title turns the start-of-match reminders for all of them on
 * and off; [remindersNotice] (why it could not be turned on, or that the system does not allow
 * notifications) shows under the title.
 */
@Composable
private fun FavoritesRow(
    favorites: List<FavoriteTeam>,
    remindersEnabled: Boolean,
    remindersNotice: String?,
    onClick: (String) -> Unit,
    onToggleReminders: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = Spacing.Screen, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            // Styled like the shared SectionTitle, which cannot share a row with the bell.
            Text(
                "Favorites",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            RemindersBell(remindersEnabled, onToggleReminders)
        }
        if (remindersNotice != null) {
            Text(
                remindersNotice,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = Spacing.Screen),
            )
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = Spacing.Screen, vertical = 8.dp),
        ) {
            items(favorites, key = { it.id }) { team -> FavoriteChip(team, onClick = { onClick(team.id) }) }
        }
    }
}

/** One favorite: logo and code on a card whose border is the team's color, once it is known. */
@Composable
private fun FavoriteChip(team: FavoriteTeam, onClick: () -> Unit) {
    val accent = rememberTeamAccent(team.imageUrl)
    Row(
        Modifier
            .clip(Shapes.Card)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, accent, Shapes.Card)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TeamLogo(team.imageUrl, contentDescription = team.name, size = 28.dp)
        Spacer(Modifier.width(8.dp))
        Text(team.code, style = LabelText.TeamCode, maxLines = 1)
    }
}

/** Bell next to the favorites: filled means start-of-match reminders are on. */
@Composable
private fun RemindersBell(enabled: Boolean, onToggle: () -> Unit) {
    IconButton(
        onClick = onToggle,
        modifier = Modifier.semantics {
            contentDescription = if (enabled) "Turn off match reminders" else "Turn on match reminders"
            stateDescription = if (enabled) "Match reminders on" else "Match reminders off"
        },
    ) {
        Icon(
            imageVector = if (enabled) BellIcon else BellOutlineIcon,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The card of one team of the list: logo, name and code. */
@Composable
private fun TeamRow(team: TeamSummary, onClick: () -> Unit) {
    AppCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Screen, vertical = Spacing.CardGap / 2),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TeamLogo(team.imageUrl, contentDescription = null, size = 32.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(team.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    team.code,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** The team list while it loads: the silhouette of [rows] team cards. */
@Composable
private fun TeamListSkeleton(rows: Int = 8) {
    SkeletonScreen {
        Column {
            repeat(rows) {
                AppCard(Modifier.fillMaxWidth().padding(horizontal = Spacing.Screen, vertical = Spacing.CardGap / 2)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SkeletonBlock(Modifier.size(32.dp), CircleShape)
                        Spacer(Modifier.width(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SkeletonBlock(Modifier.size(width = 140.dp, height = 18.dp))
                            SkeletonBlock(Modifier.size(width = 44.dp, height = 12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun TeamsContentPreview() {
    LolSitoTheme {
        TeamsContent(
            state = TeamsUiState(
                leagues = listOf(League("lck", "lck", "LCK", "KOREA", null)),
                leaguesStatus = LoadStatus.Content,
                selectedLeagueId = "lck",
                status = LoadStatus.Content,
                teams = listOf(TeamSummary("1", "Gen.G Esports", "GEN", null), TeamSummary("2", "T1", "T1", null)),
                favorites = listOf(FavoriteTeam("2", "t1", "T1", "T1", null)),
                remindersEnabled = true,
            ),
            onSelectLeague = {},
            onRetry = {},
            onRetryLeagues = {},
            onTeamClick = {},
        )
    }
}
