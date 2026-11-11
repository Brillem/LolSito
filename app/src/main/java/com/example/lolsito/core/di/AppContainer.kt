package com.example.lolsito.core.di

import com.example.lolsito.core.leagues.CachedLeaguesRepository
import com.example.lolsito.core.leagues.LeaguesRepository
import com.example.lolsito.core.leagues.RemoteLeaguesRepository
import com.example.lolsito.core.network.NetworkModule
import com.example.lolsito.feature.game.data.GameStatsRepositoryImpl
import com.example.lolsito.feature.game.domain.GameStatsRepository
import com.example.lolsito.feature.match.data.MatchDetailRepositoryImpl
import com.example.lolsito.feature.match.domain.MatchDetailRepository
import com.example.lolsito.feature.schedule.data.ScheduleRepositoryImpl
import com.example.lolsito.feature.schedule.domain.ScheduleRepository
import com.example.lolsito.feature.standings.data.StandingsRepositoryImpl
import com.example.lolsito.feature.standings.domain.StandingsRepository
import okhttp3.OkHttpClient
import java.time.Clock
import java.time.ZoneId

/** Manual dependency container, created once by [com.example.lolsito.LolSitoApplication]. */
class AppContainer {
    /** Shares one connection pool; the livestats feed uses it as is, without the gateway API key. */
    private val baseOkHttpClient by lazy { OkHttpClient() }
    private val okHttpClient by lazy { NetworkModule.createOkHttpClient(baseOkHttpClient.newBuilder()) }
    private val api by lazy { NetworkModule.createApi(client = okHttpClient) }
    private val liveStatsApi by lazy { NetworkModule.createLiveStatsApi(client = baseOkHttpClient) }

    /** One cached leagues list shared by Schedule and Standings. */
    val leaguesRepository: LeaguesRepository by lazy { CachedLeaguesRepository(RemoteLeaguesRepository(api)) }
    val scheduleRepository: ScheduleRepository by lazy { ScheduleRepositoryImpl(api) }
    val matchDetailRepository: MatchDetailRepository by lazy { MatchDetailRepositoryImpl(api) }
    val standingsRepository: StandingsRepository by lazy { StandingsRepositoryImpl(api) }
    val gameStatsRepository: GameStatsRepository by lazy { GameStatsRepositoryImpl(liveStatsApi, clock()) }

    fun zoneId(): ZoneId = ZoneId.systemDefault()

    fun clock(): Clock = Clock.systemDefaultZone()
}
