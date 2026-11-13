package com.example.lolsito.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_preferences",
    corruptionHandler = DataStoreUserPreferences.corruptionHandler,
)

/** [UserPreferences] kept in a Preferences DataStore. Failures are not caught here; callers fall back. */
class DataStoreUserPreferences(private val dataStore: DataStore<Preferences>) : UserPreferences {

    override suspend fun getScheduleLeagueIds(): Set<String>? = dataStore.data.first()[SCHEDULE_LEAGUE_IDS]

    override suspend fun saveScheduleLeagueIds(ids: Set<String>) {
        dataStore.edit { it[SCHEDULE_LEAGUE_IDS] = ids }
    }

    override suspend fun getStandingsSelection(): StandingsSelection? {
        val preferences = dataStore.data.first()
        val leagueId = preferences[STANDINGS_LEAGUE_ID] ?: return null
        return StandingsSelection(leagueId, preferences[STANDINGS_TOURNAMENT_ID])
    }

    override suspend fun saveStandingsSelection(selection: StandingsSelection) {
        dataStore.edit {
            it[STANDINGS_LEAGUE_ID] = selection.leagueId
            val tournamentId = selection.tournamentId
            if (tournamentId == null) it.remove(STANDINGS_TOURNAMENT_ID) else it[STANDINGS_TOURNAMENT_ID] = tournamentId
        }
    }

    override suspend fun getNoSpoilers(): Boolean? = dataStore.data.first()[NO_SPOILERS]

    override suspend fun saveNoSpoilers(enabled: Boolean) {
        dataStore.edit { it[NO_SPOILERS] = enabled }
    }

    /** An unparseable stored value reads as no favorites instead of failing. */
    override suspend fun getFavoriteTeams(): List<FavoriteTeam>? =
        dataStore.data.first()[FAVORITE_TEAMS]?.let(::decodeFavoriteTeams)

    override suspend fun saveFavoriteTeams(teams: List<FavoriteTeam>) {
        dataStore.edit { it[FAVORITE_TEAMS] = encodeFavoriteTeams(teams) }
    }

    override suspend fun getMatchRemindersEnabled(): Boolean? = dataStore.data.first()[MATCH_REMINDERS]

    override suspend fun saveMatchRemindersEnabled(enabled: Boolean) {
        dataStore.edit { it[MATCH_REMINDERS] = enabled }
    }

    companion object {
        /** A corrupt file is replaced by empty preferences, so persistence recovers instead of failing forever. */
        internal val corruptionHandler = ReplaceFileCorruptionHandler<Preferences> { emptyPreferences() }

        internal val SCHEDULE_LEAGUE_IDS = stringSetPreferencesKey("schedule.leagueIds")
        internal val STANDINGS_LEAGUE_ID = stringPreferencesKey("standings.leagueId")
        internal val STANDINGS_TOURNAMENT_ID = stringPreferencesKey("standings.tournamentId")
        internal val NO_SPOILERS = booleanPreferencesKey("noSpoilers")
        internal val FAVORITE_TEAMS = stringPreferencesKey("favoriteTeams")
        internal val MATCH_REMINDERS = booleanPreferencesKey("matchReminders")

        fun create(context: Context): UserPreferences =
            DataStoreUserPreferences(context.applicationContext.userPreferencesDataStore)
    }
}
