package com.example.lolsito.feature.schedule.data

import com.example.lolsito.core.network.LolEsportsApi
import com.example.lolsito.feature.schedule.domain.SchedulePage
import com.example.lolsito.feature.schedule.domain.ScheduleRepository

class ScheduleRepositoryImpl(private val api: LolEsportsApi) : ScheduleRepository {
    override suspend fun getSchedule(leagueIds: List<String>, pageToken: String?): SchedulePage =
        api.getSchedule(leagueIds.joinToString(","), pageToken).data.schedule.toSchedulePage()
}
