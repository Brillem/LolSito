package com.example.lolsito.feature.settings.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lolsito.core.preferences.MarkedMatch
import com.example.lolsito.core.ui.AppCard
import com.example.lolsito.core.ui.AppTopBar
import com.example.lolsito.core.ui.InfoText
import com.example.lolsito.core.ui.SectionTitle
import com.example.lolsito.core.ui.Spacing
import com.example.lolsito.ui.theme.LolSitoTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Container: wires the ViewModel to the stateless [SettingsContent]. */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, zoneId: ZoneId, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The same choices change on other screens, and notifications in the system settings: read them again.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
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
    SettingsContent(
        state = state,
        zoneId = zoneId,
        onBack = onBack,
        onToggleNoSpoilers = viewModel::toggleNoSpoilers,
        onToggleReminders = onToggleReminders,
        onRemoveReminder = viewModel::removeReminder,
        modifier = modifier,
    )
}

@Composable
fun SettingsContent(
    state: SettingsUiState,
    zoneId: ZoneId,
    onBack: () -> Unit,
    onToggleNoSpoilers: () -> Unit,
    onToggleReminders: () -> Unit,
    onRemoveReminder: (matchId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        AppTopBar("Settings", onBack)
        LazyColumn(Modifier.fillMaxSize()) {
            item(key = "hide-results") {
                SwitchRow(
                    title = "Hide results",
                    subtitle = "Finished scores stay hidden until you reveal them",
                    checked = state.noSpoilers,
                    onToggle = onToggleNoSpoilers,
                )
            }
            item(key = "favorite-reminders") {
                SwitchRow(
                    title = "Favorite team reminders",
                    subtitle = "Get notified when a match of a favorite team starts",
                    checked = state.remindersEnabled,
                    onToggle = onToggleReminders,
                    notice = state.remindersNotice,
                )
            }
            item(key = "upcoming-title") { SectionTitle("Upcoming reminders", Modifier.padding(top = 16.dp)) }
            if (state.upcomingReminders.isEmpty()) {
                item(key = "upcoming-empty") {
                    InfoText("No reminders yet. Tap \"Remind me\" on a match that has not started.")
                }
            } else {
                items(state.upcomingReminders, key = { it.matchId }) { match ->
                    ReminderRow(match, zoneId, onRemove = { onRemoveReminder(match.matchId) })
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onToggle: () -> Unit, notice: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.Screen, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (notice != null) {
                Text(notice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            modifier = Modifier.semantics { contentDescription = title },
        )
    }
}

/** One marked match: its teams, league and format, and when it starts in the local time zone. */
@Composable
private fun ReminderRow(match: MarkedMatch, zoneId: ZoneId, onRemove: () -> Unit) {
    AppCard(Modifier.fillMaxWidth().padding(horizontal = Spacing.Screen, vertical = Spacing.CardGap / 2)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(match.teamsLabel, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (match.detail.isNotEmpty()) {
                    Text(match.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    reminderStartLabel(match.startTime, zoneId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = onRemove,
                modifier = Modifier.semantics { contentDescription = "Remove the reminder of ${match.teamsLabel}" },
            ) { Text("Remove") }
        }
    }
}

/** The local date and time a marked match starts, in the device's medium/short style. */
internal fun reminderStartLabel(start: Instant, zoneId: ZoneId): String =
    start.atZone(zoneId).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))

@Preview
@Composable
private fun SettingsContentPreview() {
    LolSitoTheme {
        SettingsContent(
            state = SettingsUiState(
                noSpoilers = true,
                upcomingReminders = listOf(MarkedMatch("1", "lck", Instant.parse("2026-11-20T08:00:00Z"), "GEN vs BLG", "LCK · Bo5")),
            ),
            zoneId = ZoneId.of("UTC"),
            onBack = {},
            onToggleNoSpoilers = {},
            onToggleReminders = {},
            onRemoveReminder = {},
        )
    }
}
