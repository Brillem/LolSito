package com.example.lolsito.core.di

import android.content.Context
import com.example.lolsito.core.leagues.CachedLeaguesRepository
import com.example.lolsito.core.leagues.LeaguesRepository
import com.example.lolsito.core.leagues.RemoteLeaguesRepository
import com.example.lolsito.core.network.NetworkModule
import com.example.lolsito.core.network.OfflineCache
import com.example.lolsito.core.network.OfflineState
import com.example.lolsito.core.preferences.DataStoreUserPreferences
import com.example.lolsito.core.preferences.UserPreferences
import com.example.lolsito.feature.game.data.GameStatsRepositoryImpl
import com.example.lolsito.feature.game.domain.GameStatsRepository
import com.example.lolsito.feature.match.data.MatchDetailRepositoryImpl
import com.example.lolsito.feature.match.domain.MatchDetailRepository
import com.example.lolsito.feature.reminders.domain.CheckMatchStart
import com.example.lolsito.feature.reminders.domain.NotificationAvailability
import com.example.lolsito.feature.reminders.domain.ReminderScheduler
import com.example.lolsito.feature.reminders.domain.ReminderSyncTrigger
import com.example.lolsito.feature.reminders.domain.SyncMatchReminders
import com.example.lolsito.feature.reminders.work.SystemNotificationAvailability
import com.example.lolsito.feature.reminders.work.WorkManagerReminders
import com.example.lolsito.feature.schedule.data.LiveMatchesRepositoryImpl
import com.example.lolsito.feature.schedule.data.ScheduleRepositoryImpl
import com.example.lolsito.feature.schedule.domain.LiveMatchesRepository
import com.example.lolsito.feature.schedule.domain.ScheduleRepository
import com.example.lolsito.feature.standings.data.CachedStandingsRepository
import com.example.lolsito.feature.standings.data.StandingsRepositoryImpl
import com.example.lolsito.feature.standings.domain.StandingsRepository
import com.example.lolsito.feature.teams.data.TeamDirectoryRepositoryImpl
import com.example.lolsito.feature.teams.data.TeamLineupRepositoryImpl
import com.example.lolsito.feature.teams.data.TeamsRepositoryImpl
import com.example.lolsito.feature.teams.domain.TeamDirectoryRepository
import com.example.lolsito.feature.teams.domain.TeamLineupRepository
import com.example.lolsito.feature.teams.domain.TeamsRepository
import okhttp3.OkHttpClient
import java.io.File
import java.time.Clock
import java.time.ZoneId

/** Manual dependency container, created once by [com.example.lolsito.LolSitoApplication]. */
class AppContainer(private val context: Context) {
    /** Shares one connection pool; the livestats feed uses it as is, without the gateway API key. */
    private val baseOkHttpClient by lazy { OkHttpClient() }
    /** Observed by the app to tell that stored data is shown. */
    val offlineState = OfflineState()

    /** Gateway client of the screens: without a network it answers with the last stored copy. */
    private val okHttpClient by lazy {
        NetworkModule.createOkHttpClient(
            baseOkHttpClient.newBuilder(),
            OfflineCache(File(context.cacheDir, "gateway_http_cache"), offlineState),
        )
    }
    private val api by lazy { NetworkModule.createApi(client = okHttpClient) }

    /** Gateway without stored copies, for work that must never act on old data. */
    private val networkOnlyApi by lazy {
        NetworkModule.createApi(client = NetworkModule.createOkHttpClient(baseOkHttpClient.newBuilder()))
    }
    private val liveStatsApi by lazy { NetworkModule.createLiveStatsApi(client = baseOkHttpClient) }

    /** One cached leagues list shared by Schedule and Standings. */
    val leaguesRepository: LeaguesRepository by lazy { CachedLeaguesRepository(RemoteLeaguesRepository(api)) }
    val scheduleRepository: ScheduleRepository by lazy { ScheduleRepositoryImpl(api) }
    val liveMatchesRepository: LiveMatchesRepository by lazy { LiveMatchesRepositoryImpl(api) }
    val matchDetailRepository: MatchDetailRepository by lazy { MatchDetailRepositoryImpl(api) }
    /** One cached standings source shared by Standings, Teams and Schedule; pull to refresh skips the cache. */
    val standingsRepository: StandingsRepository by lazy { CachedStandingsRepository(StandingsRepositoryImpl(api), clock()) }
    val teamsRepository: TeamsRepository by lazy { TeamsRepositoryImpl(api) }
    val teamDirectoryRepository: TeamDirectoryRepository by lazy { TeamDirectoryRepositoryImpl(api) }
    val teamLineupRepository: TeamLineupRepository by lazy { TeamLineupRepositoryImpl(matchDetailRepository, liveStatsApi) }
    val gameStatsRepository: GameStatsRepository by lazy { GameStatsRepositoryImpl(liveStatsApi, clock()) }

    val userPreferences: UserPreferences by lazy { DataStoreUserPreferences.create(context) }

    private val workManagerReminders by lazy { WorkManagerReminders(context, clock()) }
    val reminderScheduler: ReminderScheduler get() = workManagerReminders
    val reminderSyncTrigger: ReminderSyncTrigger get() = workManagerReminders

    val notificationAvailability: NotificationAvailability by lazy { SystemNotificationAvailability(context) }

    /**
     * Run by the background sync worker. Everything it asks the gateway comes from the network only (the
     * leagues from a list of its own, not the one the screens share), so a sync that loses the connection
     * fails and is retried instead of scheduling reminders from a stored copy or turning the app offline.
     */
    val syncMatchReminders: SyncMatchReminders by lazy {
        SyncMatchReminders(
            userPreferences,
            TeamsRepositoryImpl(networkOnlyApi),
            CachedLeaguesRepository(RemoteLeaguesRepository(networkOnlyApi)),
            ScheduleRepositoryImpl(networkOnlyApi),
            reminderScheduler,
            clock(),
        )
    }

    /** Run by each check of a reminder. Its live feed comes from the network only, never from a stored copy. */
    val checkMatchStart: CheckMatchStart by lazy {
        CheckMatchStart(userPreferences, LiveMatchesRepositoryImpl(networkOnlyApi), reminderScheduler, clock())
    }

    /**
     * The app came to the foreground: keeps the 6-hour reminder sync scheduled (idempotent) and syncs now
     * (a newer request replaces a pending one); both wait for a network.
     */
    fun onAppForegrounded() {
        workManagerReminders.ensurePeriodicSync()
        workManagerReminders.requestSyncIfIdle()
    }

    fun zoneId(): ZoneId = ZoneId.systemDefault()

    fun clock(): Clock = Clock.systemDefaultZone()
}
