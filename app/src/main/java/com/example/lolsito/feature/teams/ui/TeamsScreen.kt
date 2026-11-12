package com.example.lolsito.feature.teams.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lolsito.core.model.League
import com.example.lolsito.core.preferences.FavoriteTeam
import com.example.lolsito.core.ui.LoadStatus
import com.example.lolsito.core.ui.LoadStatusView
import com.example.lolsito.core.ui.TeamLogo
import com.example.lolsito.feature.teams.domain.TeamSummary
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
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshFavorites() }
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
        modifier = modifier,
    )
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
) {
    Column(modifier.fillMaxSize()) {
        if (state.favorites.isNotEmpty()) {
            FavoritesRow(state.favorites, state.remindersEnabled, state.remindersNotice, onFavoriteClick, onToggleReminders)
        }
        LeaguePicker(
            label = state.selectedLeagueName,
            leagues = state.leagues,
            onSelect = onSelectLeague,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        )
        if (state.leaguesStatus is LoadStatus.Error) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Couldn't load leagues",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRetryLeagues) { Text("Retry") }
            }
        }
        LoadStatusView(state.status, "No teams for this league right now", onRetry) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.teams, key = { it.id }) { team -> TeamRow(team, onClick = { onTeamClick(team.id) }) }
            }
        }
    }
}

/**
 * The favorites above the league picker, one compact card each; tapping one opens its profile. The
 * bell next to the title turns the start-of-match reminders for all of them on and off; [remindersNotice]
 * (why it could not be turned on) shows under the title.
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
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Favorites",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            RemindersBell(remindersEnabled, onToggleReminders)
        }
        if (remindersNotice != null) {
            Text(
                remindersNotice,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            items(favorites, key = { it.id }) { team ->
                Row(
                    Modifier
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                        .clickable { onClick(team.id) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TeamLogo(team.imageUrl, contentDescription = team.name, size = 28.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(team.code, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
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

/** Dropdown of [leagues]; with none it only names the current selection and cannot be opened. */
@Composable
private fun LeaguePicker(label: String, leagues: List<League>, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, enabled = leagues.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text("$label ▾", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = expanded && leagues.isNotEmpty(), onDismissRequest = { expanded = false }) {
            leagues.forEach { league ->
                DropdownMenuItem(
                    text = { Text(league.name) },
                    onClick = {
                        expanded = false
                        onSelect(league.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun TeamRow(team: TeamSummary, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TeamLogo(team.imageUrl, contentDescription = null, size = 32.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                team.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(team.code, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
