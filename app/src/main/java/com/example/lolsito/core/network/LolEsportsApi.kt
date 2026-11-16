package com.example.lolsito.core.network

import com.example.lolsito.core.network.dto.EventDetailsResponse
import com.example.lolsito.core.network.dto.LeaguesResponse
import com.example.lolsito.core.network.dto.ScheduleResponse
import com.example.lolsito.core.network.dto.StandingsResponse
import com.example.lolsito.core.network.dto.TeamsResponse
import com.example.lolsito.core.network.dto.TournamentsResponse
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

interface LolEsportsApi {
    @GET("getLeagues")
    suspend fun getLeagues(): LeaguesResponse

    /** [leagueIds] is a comma-separated list. Cursor tokens are only valid for the same filter. */
    @GET("getSchedule")
    suspend fun getSchedule(
        @Query("leagueId") leagueIds: String,
        @Query("pageToken") pageToken: String? = null,
    ): ScheduleResponse

    /**
     * Events being played right now, of every league, with the same shape as a schedule page (no
     * pagination). Empty when nothing is live. Entries of another `type` than `match` (shows) are
     * possible, so callers must filter them. Never stored for offline use: stale live data would mislead.
     */
    @Headers("Cache-Control: no-store")
    @GET("getLive")
    suspend fun getLive(): ScheduleResponse

    /** [id] is the match id (the event id of a match event). */
    @GET("getEventDetails")
    suspend fun getEventDetails(@Query("id") id: String): EventDetailsResponse

    /** Tournaments of one league, newest first. */
    @GET("getTournamentsForLeague")
    suspend fun getTournamentsForLeague(@Query("leagueId") leagueId: String): TournamentsResponse

    /**
     * Standings of a tournament: rankings for group sections, and for bracket sections (Swiss included) the
     * official layout in columns and cells, with where every match slot's team comes from.
     */
    @GET("getStandingsV3")
    suspend fun getStandingsV3(@Query("tournamentId") tournamentId: String): StandingsResponse

    /**
     * One team with its players. [id] is the team id or slug; never call it without one, since the
     * unfiltered answer is every team ever listed (about 1.5 MB).
     */
    @GET("getTeams")
    suspend fun getTeams(@Query("id") id: String): TeamsResponse
}
