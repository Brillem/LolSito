package com.example.lolsito.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lolsito.core.preferences.MarkedMatch
import com.example.lolsito.core.preferences.UserPreferences
import com.example.lolsito.feature.reminders.domain.NotificationAvailability
import com.example.lolsito.feature.reminders.domain.ReminderScheduler
import com.example.lolsito.feature.reminders.domain.ReminderSyncTrigger
import com.example.lolsito.feature.reminders.domain.cancelAllExcept
import com.example.lolsito.feature.teams.ui.TeamsViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * [noSpoilers] is the Schedule eye; [remindersEnabled] the Teams bell (favorite-team reminders), with
 * [remindersNotice] as next to the bell; [upcomingReminders] the matches marked with "Remind me", soonest first.
 */
data class SettingsUiState(
    val noSpoilers: Boolean = false,
    val remindersEnabled: Boolean = false,
    val remindersNotice: String? = null,
    val upcomingReminders: List<MarkedMatch> = emptyList(),
)

/**
 * The few app-wide choices in one place. Each row works on the same preference as the control it mirrors
 * elsewhere (the Schedule eye, the Teams bell), so either place shows the other's change once read again
 * ([refresh], whenever the screen is shown). Unreadable preferences show their defaults; a failed save keeps
 * the choice on screen, like those controls do.
 */
class SettingsViewModel(
    private val preferences: UserPreferences,
    private val reminderScheduler: ReminderScheduler,
    private val reminderSync: ReminderSyncTrigger,
    private val notifications: NotificationAvailability,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        refresh()
    }

    /** Reads every choice again; a choice the user makes meanwhile wins, since it cancels this read. */
    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val noSpoilers = read(false) { preferences.getNoSpoilers() }
            val enabled = read(false) { preferences.getMatchRemindersEnabled() }
            val upcoming = read(emptyList()) { preferences.getMarkedMatches() }.sortedBy { it.startTime }
            _uiState.update { it.copy(noSpoilers = noSpoilers, remindersEnabled = enabled, upcomingReminders = upcoming) }
            refreshNotificationsNotice()
        }
    }

    fun toggleNoSpoilers() {
        loadJob?.cancel()
        val on = !_uiState.value.noSpoilers
        _uiState.update { it.copy(noSpoilers = on) }
        viewModelScope.launch { write { preferences.saveNoSpoilers(on) } }
    }

    /** Turns favorite reminders on (the permission is granted or not needed) and asks for a sync, like the bell. */
    fun enableReminders() {
        loadJob?.cancel()
        _uiState.update { it.copy(remindersEnabled = true, remindersNotice = null) }
        refreshNotificationsNotice()
        viewModelScope.launch {
            write { preferences.saveMatchRemindersEnabled(true) }
            reminderSync.requestSync()
        }
    }

    /** Turns favorite reminders off: their reminders are cancelled at once; the marked matches keep theirs. */
    fun disableReminders() {
        loadJob?.cancel()
        _uiState.update { it.copy(remindersEnabled = false, remindersNotice = null) }
        val shown = _uiState.value.upcomingReminders
        viewModelScope.launch {
            write { preferences.saveMatchRemindersEnabled(false) }
            // The stored marks, in case the list was not read yet; the shown ones when the store is unreadable.
            val marked = read(shown) { preferences.getMarkedMatches() }
            reminderScheduler.cancelAllExcept(marked.mapTo(HashSet()) { it.matchId })
        }
    }

    /** The answer to the notification permission asked when turning favorite reminders on. */
    fun onNotificationPermissionResult(granted: Boolean) {
        if (granted) {
            enableReminders()
        } else {
            _uiState.update { it.copy(remindersEnabled = false, remindersNotice = TeamsViewModel.PERMISSION_DENIED_NOTICE) }
        }
    }

    /** As next to the Teams bell: says when the system does not allow notifications while reminders are on. */
    fun refreshNotificationsNotice() {
        val blocked = !notifications.canNotify()
        _uiState.update { state ->
            when {
                state.remindersEnabled && blocked -> state.copy(remindersNotice = TeamsViewModel.NOTIFICATIONS_OFF_NOTICE)
                state.remindersNotice == TeamsViewModel.NOTIFICATIONS_OFF_NOTICE -> state.copy(remindersNotice = null)
                else -> state
            }
        }
    }

    /** Unmarks the match; the sync asked for cancels its reminder. */
    fun removeReminder(matchId: String) {
        loadJob?.cancel()
        _uiState.update { state -> state.copy(upcomingReminders = state.upcomingReminders.filterNot { it.matchId == matchId }) }
        viewModelScope.launch {
            write { preferences.removeMarkedMatches(setOf(matchId)) }
            reminderSync.requestSync()
        }
    }

    private suspend fun <T> read(default: T, block: suspend () -> T?): T = try {
        block() ?: default
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        default
    }

    private suspend fun write(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Not remembering the choice is better than failing it.
        }
    }
}
