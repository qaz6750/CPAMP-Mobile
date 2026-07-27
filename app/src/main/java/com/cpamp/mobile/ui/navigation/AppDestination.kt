package com.cpamp.mobile.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.cpamp.mobile.R

enum class AppDestination(
    val route: String,
    @StringRes val label: Int,
    val icon: ImageVector,
) {
    Overview("overview", R.string.nav_overview, Icons.Outlined.GridView),
    Traffic("traffic", R.string.nav_traffic, Icons.Outlined.QueryStats),
    Usage("usage", R.string.nav_usage, Icons.Outlined.BarChart),
    Operations("operations", R.string.nav_operations, Icons.Outlined.AdminPanelSettings),
    Settings("settings", R.string.settings_title, Icons.Outlined.Settings),
    ;

    companion object {
        fun fromRoute(route: String?): AppDestination =
            entries.firstOrNull { it.route == route } ?: Overview
    }
}

