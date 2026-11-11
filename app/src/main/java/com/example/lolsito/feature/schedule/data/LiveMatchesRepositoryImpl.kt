package com.example.lolsito.feature.schedule.data

import com.example.lolsito.core.network.LolEsportsApi
import com.example.lolsito.feature.schedule.domain.LiveMatchesRepository
import com.example.lolsito.feature.schedule.domain.ScheduleEvent

class LiveMatchesRepositoryImpl(private val api: LolEsportsApi) : LiveMatchesRepository {
    override suspend fun getLiveMatches(): List<ScheduleEvent> = api.getLive().data.schedule.toLiveMatches()
}
