package com.walkcraft.app.nav

sealed class NavRoute(val route: String, val label: String) {
    object Home : NavRoute("home", "Home")
    object Session : NavRoute("session", "Session")
    object History : NavRoute("history", "History")
    object Settings : NavRoute("settings", "Settings")

    companion object {
        val bottom = listOf(Home, Session, History, Settings)
    }
}
