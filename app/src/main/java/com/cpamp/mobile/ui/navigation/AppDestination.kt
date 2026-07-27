package com.cpamp.mobile.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import com.cpamp.mobile.R

enum class AppDestination(
    val route: String,
    @StringRes val label: Int,
    val icon: ImageVector,
) {
    Overview("overview", R.string.nav_overview, Icons.Outlined.GridView),
    Traffic("traffic", R.string.nav_traffic, Icons.Outlined.QueryStats),
    Resources("resources", R.string.nav_resources, Icons.Outlined.Dns),
    System("system", R.string.nav_system, Icons.Outlined.Tune),
    ;

    companion object {
        fun fromRoute(route: String?): AppDestination = entries.firstOrNull { it.route == route } ?: Overview
    }
}

