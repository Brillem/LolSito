package com.example.lolsito.feature.settings.ui

import com.example.lolsito.core.preferences.MarkedMatch
import com.example.lolsito.feature.reminders.domain.MatchReminder
import com.example.lolsito.feature.teams.ui.TeamsViewModel
import com.example.lolsito.testutil.FakeNotificationAvailability
import com.example.lolsito.testutil.FakeReminderScheduler
import com.example.lolsito.testutil.FakeReminderSyncTrigger
import com.example.lolsito.testutil.FakeUserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val later = MarkedMatch("m2", "lec", Instant.parse("2026-11-21T18:00:00Z"), "G2 vs FNC", "LEC · Bo3")
    private val sooner = MarkedMatch("m1", "lck", Instant.parse("2026-11-20T08:00:00Z"), "GEN vs BLG", "LCK · Bo5")
    private val preferences = FakeUserPreferences(noSpoilers = true, matchRemindersEnabled = true, markedMatches = listOf(later, sooner))
    private val scheduler = FakeReminderScheduler()
    private val sync = FakeReminderSyncTrigger()
    private val notifications = FakeNotificationAvailability()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = SettingsViewModel(preferences, scheduler, sync, notifications)

    @Test
    fun `shows the stored choices and the marked matches, soonest first`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.noSpoilers)
        assertTrue(state.remindersEnabled)
        assertEquals(listOf(sooner, later), state.upcomingReminders)
    }

    @Test
    fun `nothing stored or an unreadable store shows everything off and no reminders`() = runTest(dispatcher) {
        preferences.readFails = true
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(SettingsUiState(), vm.uiState.value)
    }

    @Test
    fun `hide results flips the no-spoilers mode and remembers it`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.toggleNoSpoilers()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.noSpoilers)
        assertEquals(listOf(false), preferences.savedNoSpoilers)
    }

    @Test
    fun `turning favorite reminders on remembers it and asks for a sync`() = runTest(dispatcher) {
        preferences.matchRemindersEnabled = false
        val vm = viewModel()
        advanceUntilIdle()

        vm.enableReminders()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.remindersEnabled)
        assertEquals(listOf(true), preferences.savedMatchRemindersEnabled)
        assertEquals(1, sync.requests)
    }

    @Test
    fun `turning favorite reminders off cancels the favorites' reminders at once but keeps the marked ones`() = runTest(dispatcher) {
        scheduler.schedule(reminder("fav"))
        scheduler.schedule(reminder(sooner.matchId))
        val vm = viewModel()
        advanceUntilIdle()

        vm.disableReminders()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.remindersEnabled)
        assertEquals(listOf(false), preferences.savedMatchRemindersEnabled)
        assertEquals(listOf("fav"), scheduler.cancelledCalls)
        assertEquals(0, scheduler.cancelAllCalls)
        assertEquals(listOf(sooner.matchId), scheduler.reminders.keys.toList())
    }

    @Test
    fun `a denied permission leaves favorite reminders off and says why`() = runTest(dispatcher) {
        preferences.matchRemindersEnabled = false
        val vm = viewModel()
        advanceUntilIdle()

        vm.onNotificationPermissionResult(granted = false)
        assertFalse(vm.uiState.value.remindersEnabled)
        assertEquals(TeamsViewModel.PERMISSION_DENIED_NOTICE, vm.uiState.value.remindersNotice)

        vm.onNotificationPermissionResult(granted = true)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.remindersEnabled)
        assertNull(vm.uiState.value.remindersNotice)
    }

    @Test
    fun `notifications blocked by the system are pointed out while favorite reminders are on`() = runTest(dispatcher) {
        notifications.allowed = false
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(TeamsViewModel.NOTIFICATIONS_OFF_NOTICE, vm.uiState.value.remindersNotice)

        notifications.allowed = true
        vm.refreshNotificationsNotice()
        assertNull(vm.uiState.value.remindersNotice)
    }

    @Test
    fun `removing a reminder unmarks the match, drops it from the list and asks for a sync`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.removeReminder(sooner.matchId)
        advanceUntilIdle()

        assertEquals(listOf(later), vm.uiState.value.upcomingReminders)
        assertEquals(listOf(later), preferences.markedMatches)
        assertEquals(1, sync.requests)
    }

    @Test
    fun `refreshing reads again what was changed elsewhere`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        preferences.markedMatches = listOf(later)
        preferences.noSpoilers = false
        vm.refresh()
        advanceUntilIdle()

        assertEquals(listOf(later), vm.uiState.value.upcomingReminders)
        assertFalse(vm.uiState.value.noSpoilers)
    }

    private fun reminder(id: String) = MatchReminder(id, Instant.parse("2026-11-20T08:00:00Z"), "A vs B", "LCK", listOf("A", "B"))
}
