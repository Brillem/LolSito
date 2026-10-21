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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
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

@Composable
fun LolSitoApp(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = TopLevelDestination.entries.any { it.route == currentDestination?.route }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
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
            startDestination = Routes.SCHEDULE,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.SCHEDULE) {
                val zoneId = container.zoneId()
                val vm: ScheduleViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { ScheduleViewModel(container.scheduleRepository, zoneId) }
                    }
                )
                ScheduleScreen(
                    viewModel = vm,
                    zoneId = zoneId,
                    onMatchClick = { matchId -> navController.navigate(Routes.match(matchId)) },
                )
            }
            composable(Routes.STANDINGS) {
                val vm: StandingsViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            StandingsViewModel(
                                repository = container.standingsRepository,
                                loadLeagues = container.scheduleRepository::getLeagues,
                                clock = container.clock(),
                            )
                        }
                    }
                )
                StandingsScreen(
                    viewModel = vm,
                    onMatchClick = { matchId -> navController.navigate(Routes.match(matchId)) },
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

/** Switches tabs keeping one back stack per tab: state is saved on leave and restored on return. */
private fun NavHostController.navigateToTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
