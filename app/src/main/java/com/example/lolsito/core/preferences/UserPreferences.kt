package com.example.lolsito.core.preferences

/** The league and, once picked, the tournament the user last chose in Standings. */
data class StandingsSelection(val leagueId: String, val tournamentId: String?)

/**
 * The few choices the app remembers between launches. Reads return null when nothing is stored.
 * Both directions may throw (the store can be unreadable or full), so callers must fall back to
 * their defaults instead of failing.
 */
interface UserPreferences {
    /** Ids of the leagues selected in Schedule; an empty set means the user deselected all of them. */
    suspend fun getScheduleLeagueIds(): Set<String>?

    suspend fun saveScheduleLeagueIds(ids: Set<String>)

    suspend fun getStandingsSelection(): StandingsSelection?

    /** Saves league and tournament together, so a stored tournament always belongs to the stored league. */
    suspend fun saveStandingsSelection(selection: StandingsSelection)

    /** Whether the no-spoilers mode (finished results hidden) is on; null when the user never chose. */
    suspend fun getNoSpoilers(): Boolean?

    suspend fun saveNoSpoilers(enabled: Boolean)

    /** The teams the user starred, in the order they were marked; null when the user never marked one. */
    suspend fun getFavoriteTeams(): List<FavoriteTeam>?

    /** Replaces the whole list; callers read, change and save it. */
    suspend fun saveFavoriteTeams(teams: List<FavoriteTeam>)

    /** Whether the Teams bell (start-of-match reminders) is on; null when the user never chose, which means off. */
    suspend fun getMatchRemindersEnabled(): Boolean?

    suspend fun saveMatchRemindersEnabled(enabled: Boolean)
}
