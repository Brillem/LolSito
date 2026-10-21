package com.example.lolsito.core.di

import com.example.lolsito.core.network.NetworkModule
import com.example.lolsito.feature.schedule.data.ScheduleRepositoryImpl
import com.example.lolsito.feature.schedule.domain.ScheduleRepository
import java.time.ZoneId

/** Manual dependency container, created once by [com.example.lolsito.LolSitoApplication]. */
class AppContainer {
    private val okHttpClient by lazy { NetworkModule.createOkHttpClient() }
    private val api by lazy { NetworkModule.createApi(client = okHttpClient) }

    val scheduleRepository: ScheduleRepository by lazy { ScheduleRepositoryImpl(api) }

    fun zoneId(): ZoneId = ZoneId.systemDefault()
}
