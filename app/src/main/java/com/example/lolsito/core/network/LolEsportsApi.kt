package com.example.lolsito.core.network

import com.example.lolsito.core.network.dto.LeaguesResponse
import com.example.lolsito.core.network.dto.ScheduleResponse
import retrofit2.http.GET
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
}
