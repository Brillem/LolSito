package com.example.lolsito.navigation

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.compose.rememberNavController
import com.example.lolsito.core.di.AppContainer
import com.example.lolsito.feature.match.ui.MatchDetailScreen
import com.example.lolsito.feature.match.ui.MatchDetailViewModel
import com.example.lolsito.feature.schedule.ui.ScheduleScreen
import com.example.lolsito.feature.schedule.ui.ScheduleViewModel

object Routes {
    const val SCHEDULE = "schedule"
    const val MATCH_ID_ARG = "matchId"
    const val MATCH = "match/{$MATCH_ID_ARG}"

    fun match(matchId: String): String = "match/${Uri.encode(matchId)}"
}

@Composable
fun LolSitoApp(container: AppContainer) {
    val navController = rememberNavController()
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
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
