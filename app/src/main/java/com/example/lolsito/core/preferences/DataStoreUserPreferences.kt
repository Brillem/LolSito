package com.example.lolsito.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

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

    companion object {
        internal val SCHEDULE_LEAGUE_IDS = stringSetPreferencesKey("schedule.leagueIds")
        internal val STANDINGS_LEAGUE_ID = stringPreferencesKey("standings.leagueId")
        internal val STANDINGS_TOURNAMENT_ID = stringPreferencesKey("standings.tournamentId")

        fun create(context: Context): UserPreferences =
            DataStoreUserPreferences(context.applicationContext.userPreferencesDataStore)
    }
}
