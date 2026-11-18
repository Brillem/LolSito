package com.example.lolsito.feature.schedule.data

import com.example.lolsito.core.network.LolEsportsApi
import com.example.lolsito.feature.schedule.domain.SchedulePage
import com.example.lolsito.feature.schedule.domain.ScheduleRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The DTO to domain mapping runs on [mapperDispatcher], off the main thread. */
class ScheduleRepositoryImpl(
    private val api: LolEsportsApi,
    private val mapperDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ScheduleRepository {
    override suspend fun getSchedule(leagueIds: List<String>, pageToken: String?): SchedulePage {
        val response = api.getSchedule(leagueIds.joinToString(","), pageToken)
        return withContext(mapperDispatcher) { response.data.schedule.toSchedulePage() }
    }
}
