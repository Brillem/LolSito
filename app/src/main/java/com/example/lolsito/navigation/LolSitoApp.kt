package com.example.lolsito.navigation

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.lolsito.R
import com.example.lolsito.core.di.AppContainer
import com.example.lolsito.core.ui.OfflineBanner
import com.example.lolsito.core.ui.offlineBannerLabel
import com.example.lolsito.feature.game.domain.GameTeam
import com.example.lolsito.feature.game.ui.GameStatsScreen
import com.example.lolsito.feature.game.ui.GameStatsViewModel
import com.example.lolsito.feature.match.ui.MatchDetailScreen
import com.example.lolsito.feature.match.ui.MatchDetailViewModel
import com.example.lolsito.feature.schedule.ui.ScheduleScreen
import com.example.lolsito.feature.schedule.ui.ScheduleViewModel
import com.example.lolsito.feature.settings.ui.SettingsScreen
import com.example.lolsito.feature.settings.ui.SettingsViewModel
import com.example.lolsito.feature.standings.ui.StandingsScreen
import com.example.lolsito.feature.standings.ui.StandingsViewModel
import com.example.lolsito.feature.teams.ui.TeamProfileScreen
import com.example.lolsito.feature.teams.ui.TeamProfileViewModel
import com.example.lolsito.feature.teams.ui.TeamsScreen
import com.example.lolsito.feature.teams.ui.TeamsViewModel
import java.time.LocalDate

object Routes {
    const val SCHEDULE = "schedule"
    const val STANDINGS = "standings"
    const val TEAMS = "teams"
    const val SETTINGS = "settings"
    const val MATCH_ID_ARG = "matchId"
    const val MATCH = "match/{$MATCH_ID_ARG}"

    fun match(matchId: String): String = "match/${Uri.encode(matchId)}"

    const val TOURNAMENT_LEAGUE_ID_ARG = "leagueId"
    const val TOURNAMENT_ID_ARG = "tournamentId"

    /** One tournament opened on top of the screen that asked for it (a team profile, Schedule's upcoming list). */
    const val TOURNAMENT = "tournament/{$TOURNAMENT_LEAGUE_ID_ARG}/{$TOURNAMENT_ID_ARG}"

    fun tournament(leagueId: String, tournamentId: String): String =
        "tournament/${Uri.encode(leagueId)}/${Uri.encode(tournamentId)}"

    const val TEAM_ID_ARG = "teamId"
    const val TEAM_LEAGUE_ID_ARG = "leagueId"

    /** The league the team was opened from is an optional query argument. */
    const val TEAM = "team/{$TEAM_ID_ARG}?$TEAM_LEAGUE_ID_ARG={$TEAM_LEAGUE_ID_ARG}"

    /** [leagueId] is null when the team is opened from the favorites, which belong to no selected league. */
    fun team(teamId: String, leagueId: String? = null): String =
        "team/${Uri.encode(teamId)}" + (leagueId?.let { "?$TEAM_LEAGUE_ID_ARG=${Uri.encode(it)}" } ?: "")

    const val GAME_ID_ARG = "gameId"
    private const val TEAM1_ID_ARG = "team1Id"
    private const val TEAM1_CODE_ARG = "team1Code"
    private const val TEAM1_LOGO_ARG = "team1Logo"
    private const val TEAM2_ID_ARG = "team2Id"
    private const val TEAM2_CODE_ARG = "team2Code"
    private const val TEAM2_LOGO_ARG = "team2Logo"

    /** Optional query arguments, an id, a code and a logo url for each of the two teams, in that order. */
    val TEAM_ARGS = listOf(TEAM1_ID_ARG, TEAM1_CODE_ARG, TEAM1_LOGO_ARG, TEAM2_ID_ARG, TEAM2_CODE_ARG, TEAM2_LOGO_ARG)

    /** The team arguments are optional query arguments, so the route still matches `game/{gameId}`. */
    const val GAME = "game/{$GAME_ID_ARG}?" +
        "$TEAM1_ID_ARG={$TEAM1_ID_ARG}&$TEAM1_CODE_ARG={$TEAM1_CODE_ARG}&$TEAM1_LOGO_ARG={$TEAM1_LOGO_ARG}&" +
        "$TEAM2_ID_ARG={$TEAM2_ID_ARG}&$TEAM2_CODE_ARG={$TEAM2_CODE_ARG}&$TEAM2_LOGO_ARG={$TEAM2_LOGO_ARG}"

    fun game(gameId: String, teams: Map<String, GameTeam>): String = gameRoute(gameId, teams, Uri::encode)
}

/**
 * Builds the game stats route with the code and logo of each team by team id (the first two, none when
 * unknown; a team without a logo leaves that argument out). [encode] is `Uri.encode` outside tests.
 */
internal fun gameRoute(gameId: String, teams: Map<String, GameTeam>, encode: (String) -> String): String {
    val query = teams.entries.take(2).flatMapIndexed { i, (id, team) ->
        listOf(
            Routes.TEAM_ARGS[3 * i] to id,
            Routes.TEAM_ARGS[3 * i + 1] to team.code,
            Routes.TEAM_ARGS[3 * i + 2] to team.logoUrl?.takeIf { it.isNotBlank() },
        )
    }.mapNotNull { (name, value) -> value?.let { "$name=${encode(it)}" } }
    return "game/${encode(gameId)}" + if (query.isEmpty()) "" else query.joinToString("&", prefix = "?")
}

/** Teams by id from the route arguments, read through [argument]; a team missing its id or code is ignored, its logo is optional. */
internal fun gameTeams(argument: (String) -> String?): Map<String, GameTeam> =
    Routes.TEAM_ARGS.chunked(3).mapNotNull { (idArg, codeArg, logoArg) ->
        val id = argument(idArg)
        val code = argument(codeArg)
        if (id != null && code != null) id to GameTeam(code, argument(logoArg)) else null
    }.toMap()

/** Top-level destinations shown in the bottom bar. Pushed screens (match detail, game stats) hide the bar. */
private enum class TopLevelDestination(val route: String, val label: String, @DrawableRes val icon: Int) {
    SCHEDULE(Routes.SCHEDULE, "Schedule", R.drawable.ic_tab_schedule),
    STANDINGS(Routes.STANDINGS, "Tournaments", R.drawable.ic_tab_tournaments),
    TEAMS(Routes.TEAMS, "Teams", R.drawable.ic_tab_teams),
}

internal const val START_ROUTE = Routes.SCHEDULE

/**
 * Route that drives the bottom bar. On the first frame there is no back stack entry yet, so the
 * start destination stands in for it; otherwise the bar would pop in and the content would jump.
 */
internal fun bottomBarRoute(currentRoute: String?): String = currentRoute ?: START_ROUTE

/** Only a resumed entry may navigate, so a double tap during the transition pushes a single screen. */
internal fun canNavigateFrom(state: Lifecycle.State): Boolean = state == Lifecycle.State.RESUMED

internal fun showsBottomBar(route: String): Boolean = TopLevelDestination.entries.any { it.route == route }

/**
 * [matchToOpen] is a match a reminder asked to open: once the navigation graph is ready, its Match
 * detail is pushed on the current tab and [onMatchOpened] is called.
 */
@Composable
fun LolSitoApp(container: AppContainer, matchToOpen: String? = null, onMatchOpened: () -> Unit = {}) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val graphReady = backStackEntry != null
    LaunchedEffect(matchToOpen, graphReady) {
        val matchId = matchToOpen ?: return@LaunchedEffect
        if (!graphReady) return@LaunchedEffect
        navController.openMatchFromReminder(matchId)
        onMatchOpened()
    }
    val currentDestination = backStackEntry?.destination
    val barRoute = bottomBarRoute(currentDestination?.route)
    val showBottomBar = showsBottomBar(barRoute)
    val cachedSince by container.offlineState.cachedSince.collectAsStateWithLifecycle()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            cachedSince?.let { since ->
                val zone = container.zoneId()
                OfflineBanner(offlineBannerLabel(since, zone, today = LocalDate.now(container.clock().withZone(zone))))
            }
        },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = currentDestination?.hierarchy?.any { it.route == destination.route }
                                ?: (destination.route == barRoute),
                            onClick = { navController.navigateToTopLevel(destination.route) },
                            icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                            label = { Text(destination.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.inversePrimary,
                                selectedTextColor = MaterialTheme.colorScheme.inversePrimary,
                                indicatorColor = Color.Transparent,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = START_ROUTE,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { screenEnter(isTabSwitch(initialState.destination.route, targetState.destination.route)) },
            exitTransition = { screenExit() },
            popEnterTransition = { screenPopEnter() },
            popExitTransition = { screenPopExit(isTabSwitch(initialState.destination.route, targetState.destination.route)) },
        ) {
            composable(Routes.SCHEDULE) { entry ->
                val vm: ScheduleViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            ScheduleViewModel(
                                repository = container.scheduleRepository,
                                liveRepository = container.liveMatchesRepository,
                                leaguesRepository = container.leaguesRepository,
                                preferences = container.userPreferences,
                                zoneId = container.zoneId(),
                                standingsRepository = container.standingsRepository,
                                clock = container.clock(),
                            )
                        }
                    }
                )
                ScheduleScreen(
                    viewModel = vm,
                    onMatchClick = { matchId -> navController.navigateFrom(entry, Routes.match(matchId)) },
                    onTournamentClick = { leagueId, tournamentId ->
                        navController.navigateFrom(entry, Routes.tournament(leagueId, tournamentId))
                    },
                    onSettingsClick = { navController.navigateFrom(entry, Routes.SETTINGS) },
                )
            }
            composable(Routes.SETTINGS) {
                val vm: SettingsViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            SettingsViewModel(
                                preferences = container.userPreferences,
                                reminderScheduler = container.reminderScheduler,
                                reminderSync = container.reminderSyncTrigger,
                                notifications = container.notificationAvailability,
                            )
                        }
                    }
                )
                SettingsScreen(viewModel = vm, zoneId = container.zoneId(), onBack = { navController.navigateUp() })
            }
            composable(Routes.STANDINGS) { entry ->
                val vm: StandingsViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            StandingsViewModel(
                                repository = container.standingsRepository,
                                leaguesRepository = container.leaguesRepository,
                                preferences = container.userPreferences,
                                clock = container.clock(),
                                scheduleRepository = container.scheduleRepository,
                                zoneId = container.zoneId(),
                                savedStateHandle = createSavedStateHandle(),
                            )
                        }
                    }
                )
                StandingsScreen(
                    viewModel = vm,
                    onMatchClick = { matchId -> navController.navigateFrom(entry, Routes.match(matchId)) },
                    onTeamClick = { teamId -> navController.navigateFrom(entry, Routes.team(teamId, vm.uiState.value.selectedLeagueId)) },
                )
            }
            composable(
                Routes.TOURNAMENT,
                arguments = listOf(
                    navArgument(Routes.TOURNAMENT_LEAGUE_ID_ARG) { type = NavType.StringType },
                    navArgument(Routes.TOURNAMENT_ID_ARG) { type = NavType.StringType },
                ),
            ) { entry ->
                val vm: StandingsViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            val handle = createSavedStateHandle()
                            // Seeded once from the route; after that the handle keeps any pick made on this screen.
                            if (!handle.contains(StandingsViewModel.KEY_LEAGUE_ID)) {
                                handle[StandingsViewModel.KEY_LEAGUE_ID] = handle.get<String>(Routes.TOURNAMENT_LEAGUE_ID_ARG)
                                handle[StandingsViewModel.KEY_TOURNAMENT_ID] = handle.get<String>(Routes.TOURNAMENT_ID_ARG)
                            }
                            StandingsViewModel(
                                repository = container.standingsRepository,
                                leaguesRepository = container.leaguesRepository,
                                preferences = container.userPreferences,
                                clock = container.clock(),
                                scheduleRepository = container.scheduleRepository,
                                zoneId = container.zoneId(),
                                savedStateHandle = handle,
                                rememberSelection = false,
                            )
                        }
                    }
                )
                StandingsScreen(
                    viewModel = vm,
                    onMatchClick = { matchId -> navController.navigateFrom(entry, Routes.match(matchId)) },
                    onTeamClick = { teamId -> navController.navigateFrom(entry, Routes.team(teamId, vm.uiState.value.selectedLeagueId)) },
                    onBack = { navController.navigateUp() },
                )
            }
            composable(Routes.TEAMS) { entry ->
                val vm: TeamsViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            TeamsViewModel(
                                standingsRepository = container.standingsRepository,
                                leaguesRepository = container.leaguesRepository,
                                preferences = container.userPreferences,
                                clock = container.clock(),
                                reminderScheduler = container.reminderScheduler,
                                reminderSync = container.reminderSyncTrigger,
                                notifications = container.notificationAvailability,
                                teamDirectory = container.teamDirectoryRepository,
                            )
                        }
                    }
                )
                TeamsScreen(
                    viewModel = vm,
                    onTeamClick = { teamId, leagueId -> navController.navigateFrom(entry, Routes.team(teamId, leagueId)) },
                    onFavoriteClick = { teamId -> navController.navigateFrom(entry, Routes.team(teamId)) },
                )
            }
            composable(
                Routes.TEAM,
                arguments = listOf(
                    navArgument(Routes.TEAM_ID_ARG) { type = NavType.StringType },
                    navArgument(Routes.TEAM_LEAGUE_ID_ARG) { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { entry ->
                val teamId = requireNotNull(entry.arguments?.getString(Routes.TEAM_ID_ARG))
                val originLeagueId = entry.arguments?.getString(Routes.TEAM_LEAGUE_ID_ARG)
                val vm: TeamProfileViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            TeamProfileViewModel(
                                teamId = teamId,
                                originLeagueId = originLeagueId,
                                teamsRepository = container.teamsRepository,
                                scheduleRepository = container.scheduleRepository,
                                leaguesRepository = container.leaguesRepository,
                                preferences = container.userPreferences,
                                lineupRepository = container.teamLineupRepository,
                                standingsRepository = container.standingsRepository,
                                clock = container.clock(),
                                zoneId = container.zoneId(),
                                reminderSync = container.reminderSyncTrigger,
                            )
                        }
                    }
                )
                TeamProfileScreen(
                    viewModel = vm,
                    onBack = { navController.navigateUp() },
                    onMatchClick = { matchId -> navController.navigateFrom(entry, Routes.match(matchId)) },
                    onTournamentClick = { leagueId, tournamentId ->
                        navController.navigateFrom(entry, Routes.tournament(leagueId, tournamentId))
                    },
                )
            }
            composable(
                Routes.MATCH,
                arguments = listOf(navArgument(Routes.MATCH_ID_ARG) { type = NavType.StringType }),
            ) { entry ->
                val matchId = requireNotNull(entry.arguments?.getString(Routes.MATCH_ID_ARG))
                val vm: MatchDetailViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            MatchDetailViewModel(
                                matchId = matchId,
                                repository = container.matchDetailRepository,
                                gameWinners = container.gameStatsRepository::getWinnerTeamId,
                                preferences = container.userPreferences,
                                teamsRepository = container.teamsRepository,
                                scheduleRepository = container.scheduleRepository,
                                leaguesRepository = container.leaguesRepository,
                                zoneId = container.zoneId(),
                                reminderSync = container.reminderSyncTrigger,
                            )
                        }
                    }
                )
                MatchDetailScreen(
                    viewModel = vm,
                    onBack = { navController.navigateUp() },
                    onGameClick = { link ->
                        navController.navigateFrom(entry, Routes.game(link.gameId, link.teams))
                    },
                    onTeamClick = { teamId ->
                        navController.navigateFrom(entry, Routes.team(teamId))
                    },
                    onMatchClick = { otherMatchId -> navController.openMatchFromMatch(entry, otherMatchId) },
                )
            }
            composable(
                Routes.GAME,
                arguments = listOf(
                    navArgument(Routes.GAME_ID_ARG) { type = NavType.StringType },
                ) + Routes.TEAM_ARGS.map { name ->
                    navArgument(name) { type = NavType.StringType; nullable = true; defaultValue = null }
                },
            ) { entry ->
                val arguments = entry.arguments
                val gameId = requireNotNull(arguments?.getString(Routes.GAME_ID_ARG))
                val vm: GameStatsViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { GameStatsViewModel(gameId, container.gameStatsRepository) }
                    }
                )
                GameStatsScreen(
                    viewModel = vm,
                    teams = gameTeams { name -> arguments?.getString(name) },
                    onBack = { navController.navigateUp() },
                )
            }
        }
    }
}

/**
 * Pushes [route] (match detail, game stats) from [from] on the current tab's back stack. A double tap
 * would otherwise push it twice: the first navigate moves [from] out of RESUMED, so the second tap is
 * dropped; single top guards the rest.
 */
private fun NavHostController.navigateFrom(from: NavBackStackEntry, route: String) {
    if (!canNavigateFrom(from.lifecycle.currentState)) return
    navigate(route) { launchSingleTop = true }
}

/**
 * Pushes Match detail of [matchId] from the Match detail [from] (a previous meeting of its teams). Not single
 * top: that would reuse [from], which keeps showing its own match. The resumed check alone drops a double tap.
 */
private fun NavHostController.openMatchFromMatch(from: NavBackStackEntry, matchId: String) {
    if (!canNavigateFrom(from.lifecycle.currentState)) return
    navigate(Routes.match(matchId))
}

/**
 * Pushes Match detail of [matchId] for a tapped reminder, unless that same match is already on top.
 * Not single top: that would reuse an open Match detail of another match, which keeps showing its own.
 */
private fun NavHostController.openMatchFromReminder(matchId: String) {
    val current = currentBackStackEntry
    if (current?.destination?.route == Routes.MATCH && current.arguments?.getString(Routes.MATCH_ID_ARG) == matchId) return
    navigate(Routes.match(matchId))
}

/** Switches tabs keeping one back stack per tab: state is saved on leave and restored on return. */
private fun NavHostController.navigateToTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
