package com.example.lolsito.testutil

import com.example.lolsito.core.network.LiveStatsApi
import com.example.lolsito.core.network.LolEsportsJson
import com.example.lolsito.core.network.dto.LiveStatsDetailsResponse
import com.example.lolsito.core.network.dto.LiveStatsWindowResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import retrofit2.Response

/**
 * A [LiveStatsApi] whose answers are lambdas, so a test can make either feed slow, never answer or
 * fail on virtual time. It records the startingTime of every call and whether a call was cancelled.
 */
class FakeLiveStatsApi(
    var window: suspend () -> Response<LiveStatsWindowResponse> = { windowResponse() },
    var details: suspend () -> Response<LiveStatsDetailsResponse> = { detailsResponse() },
) : LiveStatsApi {
    val windowStartingTimes = mutableListOf<String>()
    val detailsStartingTimes = mutableListOf<String>()
    var windowCancelled = false
        private set
    var detailsCancelled = false
        private set

    override suspend fun getWindow(gameId: String, startingTime: String): Response<LiveStatsWindowResponse> {
        windowStartingTimes += startingTime
        try {
            return window()
        } catch (e: CancellationException) {
            windowCancelled = true
            throw e
        }
    }

    override suspend fun getDetails(gameId: String, startingTime: String): Response<LiveStatsDetailsResponse> {
        detailsStartingTimes += startingTime
        try {
            return details()
        } catch (e: CancellationException) {
            detailsCancelled = true
            throw e
        }
    }

    companion object {
        fun windowResponse(): Response<LiveStatsWindowResponse> =
            Response.success(LolEsportsJson.decodeFromString(Fixtures.read("livestats_window.json")))

        fun detailsResponse(): Response<LiveStatsDetailsResponse> =
            Response.success(LolEsportsJson.decodeFromString(Fixtures.read("livestats_details.json")))

        /** A details answer that never comes. */
        suspend fun never(): Nothing = awaitCancellation()
    }
}
