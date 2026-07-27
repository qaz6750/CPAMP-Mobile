package com.cpamp.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cpamp.mobile.ui.navigation.AppDestination
import com.cpamp.mobile.ui.navigation.MainNavigationScaffold
import com.cpamp.mobile.ui.screens.OverviewPreviewScreen
import com.cpamp.mobile.ui.screens.ResourcesPreviewScreen
import com.cpamp.mobile.ui.screens.SystemPreviewScreen
import com.cpamp.mobile.ui.screens.TrafficPreviewScreen

@Composable
fun CPAMPMobileApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val current = AppDestination.fromRoute(backStackEntry?.destination?.route)

    MainNavigationScaffold(
        currentDestination = current,
        onNavigate = { destination ->
            navController.navigate(destination.route) {
                popUpTo(AppDestination.Overview.route) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        },
    ) { contentPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Overview.route,
        ) {
            composable(AppDestination.Overview.route) { OverviewPreviewScreen(contentPadding) }
            composable(AppDestination.Traffic.route) { TrafficPreviewScreen(contentPadding) }
            composable(AppDestination.Resources.route) { ResourcesPreviewScreen(contentPadding) }
            composable(AppDestination.System.route) { SystemPreviewScreen(contentPadding) }
        }
    }
}

