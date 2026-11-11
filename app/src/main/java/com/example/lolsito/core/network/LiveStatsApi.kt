package com.example.lolsito.core.network

import com.example.lolsito.core.network.dto.LiveStatsWindowResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface LiveStatsApi {
    /**
     * Frames of game [gameId] in the 10-second window that starts at [startingTime] (RFC 3339,
     * aligned to 10 seconds, or the feed answers 400). The feed answers 204 with no body when the
     * game has no frames there (not started, unneeded, or a window before its start), so the raw
     * [Response] is returned instead of a non-null body.
     */
    @GET("window/{gameId}")
    suspend fun getWindow(
        @Path("gameId") gameId: String,
        @Query("startingTime") startingTime: String,
    ): Response<LiveStatsWindowResponse>
}
