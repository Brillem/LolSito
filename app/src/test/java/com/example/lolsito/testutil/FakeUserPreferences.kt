package com.example.lolsito.testutil

import com.example.lolsito.core.preferences.StandingsSelection
import com.example.lolsito.core.preferences.UserPreferences
import java.io.IOException

/** In-memory [UserPreferences]; the saved lists record every write in order. */
class FakeUserPreferences(
    var scheduleLeagueIds: Set<String>? = null,
    var standingsSelection: StandingsSelection? = null,
    var noSpoilers: Boolean? = null,
) : UserPreferences {
    var readFails = false
    var writeFails = false
    var reads = 0
    val savedScheduleLeagueIds = mutableListOf<Set<String>>()
    val savedStandingsSelections = mutableListOf<StandingsSelection>()
    val savedNoSpoilers = mutableListOf<Boolean>()

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
}
