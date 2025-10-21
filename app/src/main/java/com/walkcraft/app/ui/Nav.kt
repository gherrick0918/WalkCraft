package com.walkcraft.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.walkcraft.app.ui.screens.DeviceSetupScreen
import com.walkcraft.app.ui.screens.HistoryScreen
import com.walkcraft.app.ui.screens.HomeScreen
import com.walkcraft.app.ui.screens.RunScreen
import com.walkcraft.app.ui.screens.SNACKBAR_SAVED_SESSION_ID_KEY
import com.walkcraft.app.ui.screens.SessionDetailScreen

object Routes {
    const val HOME = "home"
    const val DEVICE_SETUP = "deviceSetup"
    const val RUN = "run"
    const val HISTORY = "history"
    const val HISTORY_DETAIL_ARG = "sessionId"
    const val HISTORY_DETAIL = "history/detail/{$HISTORY_DETAIL_ARG}"

    fun historyDetail(sessionId: String) = "history/detail/$sessionId"
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onDeviceSetup = { nav.navigate(Routes.DEVICE_SETUP) },
                onRun = {
                    nav.navigate(Routes.RUN) {
                        launchSingleTop = true
                    }
                },
                onHistory = { nav.navigate(Routes.HISTORY) }
            )
        }
        composable(Routes.DEVICE_SETUP) {
            DeviceSetupScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.RUN) {
            RunScreen(
                onBack = { nav.popBackStack() },
                onFinished = { sessionId ->
                    nav.currentBackStackEntry
                        ?.savedStateHandle
                        ?.set(SNACKBAR_SAVED_SESSION_ID_KEY, sessionId ?: "")
                    nav.popBackStack(Routes.RUN, inclusive = true)
                    nav.navigate(Routes.HISTORY) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(Routes.HISTORY) {
            HistoryScreen(navController = nav, onBack = { nav.popBackStack() })
        }
        composable(
            route = Routes.HISTORY_DETAIL,
            arguments = listOf(navArgument(Routes.HISTORY_DETAIL_ARG) { type = NavType.StringType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString(Routes.HISTORY_DETAIL_ARG)
                ?: return@composable
            SessionDetailScreen(
                sessionId = sessionId,
                onBack = { nav.popBackStack() }
            )
        }
    }
}
