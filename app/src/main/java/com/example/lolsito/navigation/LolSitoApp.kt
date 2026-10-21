package com.example.lolsito.navigation

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.Lifecycle
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
import com.example.lolsito.feature.match.ui.MatchDetailScreen
import com.example.lolsito.feature.match.ui.MatchDetailViewModel
import com.example.lolsito.feature.schedule.ui.ScheduleScreen
import com.example.lolsito.feature.schedule.ui.ScheduleViewModel
import com.example.lolsito.feature.standings.ui.StandingsScreen
import com.example.lolsito.feature.standings.ui.StandingsViewModel

object Routes {
    const val SCHEDULE = "schedule"
    const val STANDINGS = "standings"
    const val MATCH_ID_ARG = "matchId"
    const val MATCH = "match/{$MATCH_ID_ARG}"

    fun match(matchId: String): String = "match/${Uri.encode(matchId)}"
}

/** Top-level destinations shown in the bottom bar. Pushed screens (match detail) hide the bar. */
private enum class TopLevelDestination(val route: String, val label: String, @DrawableRes val icon: Int) {
    SCHEDULE(Routes.SCHEDULE, "Schedule", R.drawable.ic_tab_schedule),
    STANDINGS(Routes.STANDINGS, "Standings", R.drawable.ic_tab_standings),
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

@Composable
fun LolSitoApp(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val barRoute = bottomBarRoute(currentDestination?.route)
    val showBottomBar = showsBottomBar(barRoute)
    Scaffold(
        modifier = Modifier.fillMaxSize(),
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
        ) {
            composable(Routes.SCHEDULE) { entry ->
                val vm: ScheduleViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            ScheduleViewModel(container.scheduleRepository, container.leaguesRepository, container.zoneId())
                        }
                    }
                )
                ScheduleScreen(
                    viewModel = vm,
                    onMatchClick = { matchId -> navController.navigateToMatch(from = entry, matchId = matchId) },
                )
            }
            composable(Routes.STANDINGS) { entry ->
                val vm: StandingsViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            StandingsViewModel(
                                repository = container.standingsRepository,
                                leaguesRepository = container.leaguesRepository,
                                clock = container.clock(),
                                savedStateHandle = createSavedStateHandle(),
                            )
                        }
                    }
                )
                StandingsScreen(
                    viewModel = vm,
                    onMatchClick = { matchId -> navController.navigateToMatch(from = entry, matchId = matchId) },
                )
            }
            composable(
                Routes.MATCH,
                arguments = listOf(navArgument(Routes.MATCH_ID_ARG) { type = NavType.StringType }),
            ) { entry ->
                val matchId = requireNotNull(entry.arguments?.getString(Routes.MATCH_ID_ARG))
                val vm: MatchDetailViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { MatchDetailViewModel(matchId, container.matchDetailRepository) }
                    }
                )
                MatchDetailScreen(viewModel = vm, onBack = { navController.navigateUp() })
            }
        }
    }
}

/**
 * Opens match detail from [from]. A double tap would otherwise push it twice: the first navigate
 * moves [from] out of RESUMED, so the second tap is dropped; single top guards the rest.
 */
private fun NavHostController.navigateToMatch(from: NavBackStackEntry, matchId: String) {
    if (!canNavigateFrom(from.lifecycle.currentState)) return
    navigate(Routes.match(matchId)) { launchSingleTop = true }
}

/** Switches tabs keeping one back stack per tab: state is saved on leave and restored on return. */
private fun NavHostController.navigateToTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
