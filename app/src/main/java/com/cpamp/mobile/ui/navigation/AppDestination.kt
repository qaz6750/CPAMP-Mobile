package com.cpamp.mobile.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.cpamp.mobile.R

enum class AppDestination(
    val route: String,
    @StringRes val label: Int,
    val icon: ImageVector,
) {
    Overview("overview", R.string.nav_overview, Icons.Outlined.GridView),
    Usage("usage", R.string.nav_usage, Icons.Outlined.BarChart),
    Monitoring("monitoring", R.string.nav_monitoring, Icons.Outlined.QueryStats),
    Accounts("accounts", R.string.nav_accounts, Icons.Outlined.AccountCircle),
    Settings("settings", R.string.settings_title, Icons.Outlined.Settings),
    ;

    companion object {
        fun fromRoute(route: String?): AppDestination =
            when (route) {
                ACCOUNT_DETAIL_ROUTE -> Accounts
                "app-update" -> Settings
                else -> entries.firstOrNull { it.route == route } ?: Overview
            }
    }
}

const val ACCOUNT_DETAIL_ROUTE = "account/{accountId}"

