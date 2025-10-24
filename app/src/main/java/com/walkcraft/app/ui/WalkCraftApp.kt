package com.walkcraft.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.walkcraft.app.nav.NavRoute

private fun iconFor(route: String): ImageVector = when (route) {
    "home" -> Icons.Outlined.Home
    "session" -> Icons.Outlined.DirectionsWalk
    "history" -> Icons.Outlined.History
    "settings" -> Icons.Outlined.Settings
    else -> Icons.Outlined.Home
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalkCraftApp() {
    val nav = rememberNavController()
    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        topBar = {
            val currentLabel = currentLabel(currentDestination)
            TopAppBar(title = { Text(currentLabel ?: "WalkCraft") })
        },
        bottomBar = {
            NavigationBar {
                NavRoute.bottom.forEach { route ->
                    val selected = currentDestination?.hierarchy?.any { it.route == route.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                nav.navigate(route.route) {
                                    launchSingleTop = true
                                    popUpTo(nav.graph.startDestinationId) { saveState = true }
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(iconFor(route.route), contentDescription = route.label) },
                        label = { Text(route.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = nav,
            startDestination = NavRoute.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(NavRoute.Home.route) {
                HomeScreen(navToSession = { nav.navigate(NavRoute.Session.route) })
            }
            composable(NavRoute.Session.route) { SessionScreen() }
            composable(NavRoute.History.route) { HistoryScreen() }
            composable(NavRoute.Settings.route) { SettingsScreen() }
        }
    }
}
@Composable
private fun currentLabel(dest: NavDestination?): String? {
    val match = NavRoute.bottom.firstOrNull { route ->
        dest?.hierarchy?.any { it.route == route.route } == true
    }
    return match?.label
}
