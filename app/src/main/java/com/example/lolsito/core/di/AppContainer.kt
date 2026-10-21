package com.example.lolsito.core.di

import com.example.lolsito.core.network.NetworkModule
import com.example.lolsito.feature.match.data.MatchDetailRepositoryImpl
import com.example.lolsito.feature.match.domain.MatchDetailRepository
import com.example.lolsito.feature.schedule.data.ScheduleRepositoryImpl
import com.example.lolsito.feature.schedule.domain.ScheduleRepository
import com.example.lolsito.feature.standings.data.StandingsRepositoryImpl
import com.example.lolsito.feature.standings.domain.StandingsRepository
import java.time.Clock
import java.time.ZoneId

/** Manual dependency container, created once by [com.example.lolsito.LolSitoApplication]. */
class AppContainer {
    private val okHttpClient by lazy { NetworkModule.createOkHttpClient() }
    private val api by lazy { NetworkModule.createApi(client = okHttpClient) }

    val scheduleRepository: ScheduleRepository by lazy { ScheduleRepositoryImpl(api) }
    val matchDetailRepository: MatchDetailRepository by lazy { MatchDetailRepositoryImpl(api) }
    val standingsRepository: StandingsRepository by lazy { StandingsRepositoryImpl(api) }

    fun zoneId(): ZoneId = ZoneId.systemDefault()

    fun clock(): Clock = Clock.systemDefaultZone()
}
