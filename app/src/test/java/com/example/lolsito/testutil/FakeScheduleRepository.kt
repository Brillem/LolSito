package com.example.lolsito.testutil

import com.example.lolsito.feature.schedule.domain.SchedulePage
import com.example.lolsito.feature.schedule.domain.ScheduleRepository
import kotlinx.coroutines.CompletableDeferred

/** Answers with the page stored for the asked page token (null is the first page), whatever the league filter. */
class FakeScheduleRepository : ScheduleRepository {
    val pages = mutableMapOf<String?, SchedulePage>()

    /** Every request in order: the league filter and the page token. */
    val requests = mutableListOf<Pair<List<String>, String?>>()
    var error: Throwable? = null

    /** When set, getSchedule suspends on it after counting the request. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun getSchedule(leagueIds: List<String>, pageToken: String?): SchedulePage {
        requests += leagueIds to pageToken
        gate?.await()
        error?.let { throw it }
        return pages.getValue(pageToken)
    }
}
