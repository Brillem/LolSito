package com.example.lolsito.core.di

import android.content.Context
import com.example.lolsito.core.leagues.CachedLeaguesRepository
import com.example.lolsito.core.leagues.LeaguesRepository
import com.example.lolsito.core.leagues.RemoteLeaguesRepository
import com.example.lolsito.core.network.NetworkModule
import com.example.lolsito.core.preferences.DataStoreUserPreferences
import com.example.lolsito.core.preferences.UserPreferences
import com.example.lolsito.feature.game.data.GameStatsRepositoryImpl
import com.example.lolsito.feature.game.domain.GameStatsRepository
import com.example.lolsito.feature.match.data.MatchDetailRepositoryImpl
import com.example.lolsito.feature.match.domain.MatchDetailRepository
import com.example.lolsito.feature.reminders.domain.ReminderScheduler
import com.example.lolsito.feature.reminders.domain.ReminderSyncTrigger
import com.example.lolsito.feature.reminders.domain.SyncMatchReminders
import com.example.lolsito.feature.reminders.work.WorkManagerReminders
import com.example.lolsito.feature.schedule.data.LiveMatchesRepositoryImpl
import com.example.lolsito.feature.schedule.data.ScheduleRepositoryImpl
import com.example.lolsito.feature.schedule.domain.LiveMatchesRepository
import com.example.lolsito.feature.schedule.domain.ScheduleRepository
import com.example.lolsito.feature.standings.data.StandingsRepositoryImpl
import com.example.lolsito.feature.standings.domain.StandingsRepository
import com.example.lolsito.feature.teams.data.TeamLineupRepositoryImpl
import com.example.lolsito.feature.teams.data.TeamsRepositoryImpl
import com.example.lolsito.feature.teams.domain.TeamLineupRepository
import com.example.lolsito.feature.teams.domain.TeamsRepository
import okhttp3.OkHttpClient
import java.time.Clock
import java.time.ZoneId

/** Manual dependency container, created once by [com.example.lolsito.LolSitoApplication]. */
class AppContainer(private val context: Context) {
    /** Shares one connection pool; the livestats feed uses it as is, without the gateway API key. */
    private val baseOkHttpClient by lazy { OkHttpClient() }
    private val okHttpClient by lazy { NetworkModule.createOkHttpClient(baseOkHttpClient.newBuilder()) }
    private val api by lazy { NetworkModule.createApi(client = okHttpClient) }
    private val liveStatsApi by lazy { NetworkModule.createLiveStatsApi(client = baseOkHttpClient) }

    /** One cached leagues list shared by Schedule and Standings. */
    val leaguesRepository: LeaguesRepository by lazy { CachedLeaguesRepository(RemoteLeaguesRepository(api)) }
    val scheduleRepository: ScheduleRepository by lazy { ScheduleRepositoryImpl(api) }
    val liveMatchesRepository: LiveMatchesRepository by lazy { LiveMatchesRepositoryImpl(api) }
    val matchDetailRepository: MatchDetailRepository by lazy { MatchDetailRepositoryImpl(api) }
    val standingsRepository: StandingsRepository by lazy { StandingsRepositoryImpl(api) }
    val teamsRepository: TeamsRepository by lazy { TeamsRepositoryImpl(api) }
    val teamLineupRepository: TeamLineupRepository by lazy { TeamLineupRepositoryImpl(matchDetailRepository, liveStatsApi) }
    val gameStatsRepository: GameStatsRepository by lazy { GameStatsRepositoryImpl(liveStatsApi, clock()) }

    val userPreferences: UserPreferences by lazy { DataStoreUserPreferences.create(context) }

    private val workManagerReminders by lazy { WorkManagerReminders(context, clock()) }
    val reminderScheduler: ReminderScheduler get() = workManagerReminders
    val reminderSyncTrigger: ReminderSyncTrigger get() = workManagerReminders

    /** Run by the background sync worker. */
    val syncMatchReminders: SyncMatchReminders by lazy {
        SyncMatchReminders(userPreferences, teamsRepository, leaguesRepository, scheduleRepository, reminderScheduler, clock())
    }

    /** The app opened: keeps the 6-hour reminder sync scheduled and syncs now (both wait for a network). */
    fun onAppOpened() {
        workManagerReminders.ensurePeriodicSync()
        workManagerReminders.requestSync()
    }

    fun zoneId(): ZoneId = ZoneId.systemDefault()

    fun clock(): Clock = Clock.systemDefaultZone()
}
