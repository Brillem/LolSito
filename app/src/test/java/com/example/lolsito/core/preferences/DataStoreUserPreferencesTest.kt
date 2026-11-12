package com.example.lolsito.core.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
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

    private fun TestScope.dataStore(name: String = "prefs") = PreferenceDataStoreFactory.create(
        corruptionHandler = DataStoreUserPreferences.corruptionHandler,
        scope = backgroundScope,
    ) { folder.root.resolve("$name.preferences_pb") }

    private fun TestScope.preferences(name: String = "prefs"): UserPreferences =
        DataStoreUserPreferences(dataStore(name))

    private val t1 = FavoriteTeam("1", "t1", "T1", "T1", "https://img/t1.png")
    private val odd = FavoriteTeam("2", "odd-slug", "Team, \"Odd\"; 100% a+b = c/d\nñ 日本 😀", "O;D,", null)

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

    @Test
    fun `nothing stored reads no favorite teams as null`() = runTest(UnconfinedTestDispatcher()) {
        assertNull(preferences().getFavoriteTeams())
    }

    @Test
    fun `favorite teams round trip in order, keeping every field`() = runTest(UnconfinedTestDispatcher()) {
        val preferences = preferences()
        preferences.saveFavoriteTeams(listOf(t1, odd))
        assertEquals(listOf(t1, odd), preferences.getFavoriteTeams())
    }

    @Test
    fun `favorite teams round trip the empty list and later saves replace it`() = runTest(UnconfinedTestDispatcher()) {
        val preferences = preferences()
        preferences.saveFavoriteTeams(listOf(t1))
        preferences.saveFavoriteTeams(emptyList())
        assertEquals(emptyList<FavoriteTeam>(), preferences.getFavoriteTeams())

        preferences.saveFavoriteTeams(listOf(odd))
        assertEquals(listOf(odd), preferences.getFavoriteTeams())
    }

    @Test
    fun `saving favorite teams leaves the other preferences alone`() = runTest(UnconfinedTestDispatcher()) {
        val preferences = preferences()
        preferences.saveNoSpoilers(true)
        preferences.saveScheduleLeagueIds(setOf("a"))
        preferences.saveFavoriteTeams(listOf(t1))

        assertEquals(true, preferences.getNoSpoilers())
        assertEquals(setOf("a"), preferences.getScheduleLeagueIds())
    }

    @Test
    fun `an unparseable stored value reads as no favorites and a later save round trips`() = runTest(UnconfinedTestDispatcher()) {
        val dataStore = dataStore()
        val preferences = DataStoreUserPreferences(dataStore)
        for (garbage in listOf("garbage", "1,2,3", "%zz,a,b,c,d", "1,s,n,c,i,extra", ";;;")) {
            dataStore.edit { it[DataStoreUserPreferences.FAVORITE_TEAMS] = garbage }
            assertEquals("for \"$garbage\"", emptyList<FavoriteTeam>(), preferences.getFavoriteTeams())
        }

        preferences.saveFavoriteTeams(listOf(t1))
        assertEquals(listOf(t1), preferences.getFavoriteTeams())
    }

    @Test
    fun `a team stored twice reads once`() {
        val raw = encodeFavoriteTeams(listOf(t1, t1.copy(name = "Renamed"), odd))
        assertEquals(listOf(t1, odd), decodeFavoriteTeams(raw))
    }
}
