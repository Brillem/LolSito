package com.example.lolsito.testutil

import com.example.lolsito.core.preferences.FavoriteTeam
import com.example.lolsito.core.preferences.MarkedMatch
import com.example.lolsito.core.preferences.StandingsSelection
import com.example.lolsito.core.preferences.UserPreferences
import java.io.IOException

/** In-memory [UserPreferences]; the saved lists record every write in order. */
class FakeUserPreferences(
    var scheduleLeagueIds: Set<String>? = null,
    var standingsSelection: StandingsSelection? = null,
    var noSpoilers: Boolean? = null,
    var favoriteTeams: List<FavoriteTeam>? = null,
    var matchRemindersEnabled: Boolean? = null,
    var markedMatches: List<MarkedMatch>? = null,
) : UserPreferences {
    var readFails = false
    var writeFails = false
    var reads = 0
    val savedScheduleLeagueIds = mutableListOf<Set<String>>()
    val savedStandingsSelections = mutableListOf<StandingsSelection>()
    val savedNoSpoilers = mutableListOf<Boolean>()
    val savedFavoriteTeams = mutableListOf<List<FavoriteTeam>>()
    val savedMatchRemindersEnabled = mutableListOf<Boolean>()
    val savedMarkedMatches = mutableListOf<MarkedMatch>()
    val removedMarkedMatches = mutableListOf<Set<String>>()

    override suspend fun getScheduleLeagueIds(): Set<String>? {
        reads++
        if (readFails) throw IOException("read failed")
        return scheduleLeagueIds
    }

    override suspend fun saveScheduleLeagueIds(ids: Set<String>) {
        if (writeFails) throw IOException("write failed")
        savedScheduleLeagueIds += ids
        scheduleLeagueIds = ids
    }

    override suspend fun getStandingsSelection(): StandingsSelection? {
        reads++
        if (readFails) throw IOException("read failed")
        return standingsSelection
    }

    override suspend fun saveStandingsSelection(selection: StandingsSelection) {
        if (writeFails) throw IOException("write failed")
        savedStandingsSelections += selection
        standingsSelection = selection
    }

    override suspend fun getNoSpoilers(): Boolean? {
        if (readFails) throw IOException("read failed")
        return noSpoilers
    }

    override suspend fun saveNoSpoilers(enabled: Boolean) {
        if (writeFails) throw IOException("write failed")
        savedNoSpoilers += enabled
        noSpoilers = enabled
    }

    // Like the no-spoilers reads, these do not count in [reads]: a Standings test asserts that counter.
    override suspend fun getFavoriteTeams(): List<FavoriteTeam>? {
        if (readFails) throw IOException("read failed")
        return favoriteTeams
    }

    override suspend fun saveFavoriteTeams(teams: List<FavoriteTeam>) {
        if (writeFails) throw IOException("write failed")
        savedFavoriteTeams += teams
        favoriteTeams = teams
    }

    override suspend fun getMatchRemindersEnabled(): Boolean? {
        if (readFails) throw IOException("read failed")
        return matchRemindersEnabled
    }

    override suspend fun saveMatchRemindersEnabled(enabled: Boolean) {
        if (writeFails) throw IOException("write failed")
        savedMatchRemindersEnabled += enabled
        matchRemindersEnabled = enabled
    }

    override suspend fun getMarkedMatches(): List<MarkedMatch>? {
        if (readFails) throw IOException("read failed")
        return markedMatches
    }

    override suspend fun saveMarkedMatch(match: MarkedMatch) {
        if (writeFails) throw IOException("write failed")
        savedMarkedMatches += match
        val current = markedMatches.orEmpty()
        markedMatches = if (current.any { it.matchId == match.matchId }) {
            current.map { if (it.matchId == match.matchId) match else it }
        } else {
            current + match
        }
    }

    override suspend fun removeMarkedMatches(matchIds: Set<String>) {
        if (writeFails) throw IOException("write failed")
        removedMarkedMatches += matchIds
        markedMatches = markedMatches.orEmpty().filterNot { it.matchId in matchIds }
    }
}
