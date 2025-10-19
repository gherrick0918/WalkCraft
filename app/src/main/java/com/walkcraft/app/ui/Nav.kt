package com.walkcraft.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.walkcraft.app.ui.screens.DeviceSetupScreen
import com.walkcraft.app.ui.screens.HistoryScreen
import com.walkcraft.app.ui.screens.HomeScreen
import com.walkcraft.app.ui.screens.RunScreen

object Routes {
    const val HOME = "home"
    const val DEVICE_SETUP = "deviceSetup"
    const val RUN = "run"
    const val HISTORY = "history"
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onDeviceSetup = { nav.navigate(Routes.DEVICE_SETUP) },
                onRun = { nav.navigate(Routes.RUN) },
                onHistory = { nav.navigate(Routes.HISTORY) }
            )
        }
        composable(Routes.DEVICE_SETUP) {
            DeviceSetupScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.RUN) {
            RunScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.HISTORY) {
            HistoryScreen(onBack = { nav.popBackStack() })
        }
    }
}
