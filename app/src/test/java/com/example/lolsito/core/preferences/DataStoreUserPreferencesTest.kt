package com.example.lolsito.core.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreUserPreferencesTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun TestScope.preferences(name: String = "prefs"): UserPreferences {
        val file = folder.root.resolve("$name.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            corruptionHandler = DataStoreUserPreferences.corruptionHandler,
            scope = backgroundScope,
        ) { file }
        return DataStoreUserPreferences(dataStore)
    }

    @Test
    fun `nothing stored reads as null`() = runTest(UnconfinedTestDispatcher()) {
        val preferences = preferences()
        assertNull(preferences.getScheduleLeagueIds())
        assertNull(preferences.getStandingsSelection())
        assertNull(preferences.getNoSpoilers())
    }

    @Test
    fun `schedule league ids round trip including the empty set`() = runTest(UnconfinedTestDispatcher()) {
        val preferences = preferences()
        preferences.saveScheduleLeagueIds(setOf("a", "b"))
        assertEquals(setOf("a", "b"), preferences.getScheduleLeagueIds())

        preferences.saveScheduleLeagueIds(emptySet())
        assertEquals(emptySet<String>(), preferences.getScheduleLeagueIds())
    }

    @Test
    fun `standings selection round trips and a null tournament clears the stored one`() = runTest(UnconfinedTestDispatcher()) {
        val preferences = preferences()
        preferences.saveStandingsSelection(StandingsSelection("lck", "spring"))
        assertEquals(StandingsSelection("lck", "spring"), preferences.getStandingsSelection())

        preferences.saveStandingsSelection(StandingsSelection("worlds", null))
        assertEquals(StandingsSelection("worlds", null), preferences.getStandingsSelection())
    }

    @Test
    fun `no spoilers round trips in both directions`() = runTest(UnconfinedTestDispatcher()) {
        val preferences = preferences()
        preferences.saveNoSpoilers(true)
        assertEquals(true, preferences.getNoSpoilers())

        preferences.saveNoSpoilers(false)
        assertEquals(false, preferences.getNoSpoilers())
    }

    @Test
    fun `saving no spoilers leaves the other preferences alone`() = runTest(UnconfinedTestDispatcher()) {
        val preferences = preferences()
        preferences.saveScheduleLeagueIds(setOf("a"))
        preferences.saveStandingsSelection(StandingsSelection("lck", null))
        preferences.saveNoSpoilers(true)

        assertEquals(setOf("a"), preferences.getScheduleLeagueIds())
        assertEquals(StandingsSelection("lck", null), preferences.getStandingsSelection())
    }

    @Test
    fun `a corrupt file reads as nothing stored and a later save round trips`() = runTest(UnconfinedTestDispatcher()) {
        folder.root.resolve("corrupt.preferences_pb").writeBytes(ByteArray(16) { 0xFF.toByte() })
        val preferences = preferences("corrupt")

        assertNull(preferences.getScheduleLeagueIds())
        assertNull(preferences.getStandingsSelection())

        preferences.saveStandingsSelection(StandingsSelection("lck", "spring"))
        assertEquals(StandingsSelection("lck", "spring"), preferences.getStandingsSelection())
    }
}
