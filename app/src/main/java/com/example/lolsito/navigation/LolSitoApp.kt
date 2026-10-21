package com.example.lolsito.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.lolsito.core.di.AppContainer
import com.example.lolsito.feature.schedule.ui.ScheduleScreen
import com.example.lolsito.feature.schedule.ui.ScheduleViewModel

object Routes {
    const val SCHEDULE = "schedule"
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
                ScheduleScreen(viewModel = vm, zoneId = zoneId)
            }
        }
    }
}
