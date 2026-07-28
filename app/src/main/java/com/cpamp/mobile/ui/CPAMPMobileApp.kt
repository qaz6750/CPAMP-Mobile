package com.cpamp.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cpamp.mobile.ui.navigation.AppDestination
import com.cpamp.mobile.ui.navigation.MainNavigationScaffold
import com.cpamp.mobile.ui.dashboard.DashboardScreen
import com.cpamp.mobile.ui.dashboard.DashboardViewModel
import com.cpamp.mobile.ui.system.SystemScreen
import com.cpamp.mobile.ui.system.SystemViewModel
import com.cpamp.mobile.ui.monitoring.MonitoringScreen
import com.cpamp.mobile.ui.monitoring.MonitoringViewModel
import com.cpamp.mobile.ui.usage.UsageAnalyticsScreen
import com.cpamp.mobile.ui.usage.UsageAnalyticsViewModel
import com.cpamp.mobile.ui.auth.LoginScreen
import com.cpamp.mobile.ui.auth.SessionLoadingScreen
import com.cpamp.mobile.ui.auth.SessionViewModel
import com.cpamp.mobile.ui.security.AppLockUiState
import com.cpamp.mobile.ui.settings.AppearanceUiState
import com.cpamp.mobile.ui.settings.SettingsScreen
import com.cpamp.mobile.data.settings.AppLanguage
import com.cpamp.mobile.data.settings.AppTheme

@Composable
fun CPAMPMobileApp(
    appLockState: AppLockUiState,
    onSetAppLockEnabled: (Boolean) -> Unit,
    onSetAppLockTimeout: (Int) -> Unit,
    appearanceState: AppearanceUiState,
    onSetTheme: (AppTheme) -> Unit,
    onSetLanguage: (AppLanguage) -> Unit,
    onSetDynamicColor: (Boolean) -> Unit,
    onSetAllowScreenshots: (Boolean) -> Unit,
    onSetHideAddresses: (Boolean) -> Unit,
    viewModel: SessionViewModel = hiltViewModel(),
) {
    val sessionState by viewModel.state.collectAsStateWithLifecycle()
    if (sessionState.initializing) {
        SessionLoadingScreen()
        return
    }
    if (sessionState.session == null) {
        LoginScreen(
            state = sessionState,
            hideAddresses = appearanceState.settings.hideAddresses,
            onLogin = viewModel::login,
            onConnectSaved = viewModel::switchTo,
            onDeleteSaved = viewModel::delete,
            onDismissError = viewModel::clearError,
        )
        return
    }

    key(sessionState.session?.profile?.id) {
        ConnectedApp(
            sessionState = sessionState,
            onSwitchServer = viewModel::switchTo,
            onDeleteServer = viewModel::delete,
            onDisconnect = viewModel::disconnect,
            appLockState = appLockState,
            onSetAppLockEnabled = onSetAppLockEnabled,
            onSetAppLockTimeout = onSetAppLockTimeout,
            appearanceState = appearanceState,
            onSetTheme = onSetTheme,
            onSetLanguage = onSetLanguage,
            onSetDynamicColor = onSetDynamicColor,
            onSetAllowScreenshots = onSetAllowScreenshots,
            onSetHideAddresses = onSetHideAddresses,
        )
    }
}

@Composable
private fun ConnectedApp(
    sessionState: com.cpamp.mobile.ui.auth.SessionUiState,
    onSwitchServer: (String) -> Unit,
    onDeleteServer: (String) -> Unit,
    onDisconnect: () -> Unit,
    appLockState: AppLockUiState,
    onSetAppLockEnabled: (Boolean) -> Unit,
    onSetAppLockTimeout: (Int) -> Unit,
    appearanceState: AppearanceUiState,
    onSetTheme: (AppTheme) -> Unit,
    onSetLanguage: (AppLanguage) -> Unit,
    onSetDynamicColor: (Boolean) -> Unit,
    onSetAllowScreenshots: (Boolean) -> Unit,
    onSetHideAddresses: (Boolean) -> Unit,
) {
    val session = requireNotNull(sessionState.session)
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val current = AppDestination.fromRoute(backStackEntry?.destination?.route)
    val dashboardViewModel: DashboardViewModel = hiltViewModel()
    val monitoringViewModel: MonitoringViewModel = hiltViewModel()
    val usageViewModel: UsageAnalyticsViewModel = hiltViewModel()
    val systemViewModel: SystemViewModel = hiltViewModel()

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
            composable(AppDestination.Overview.route) {
                DashboardScreen(
                    contentPadding,
                    hideAddresses = appearanceState.settings.hideAddresses,
                    viewModel = dashboardViewModel,
                )
            }
            composable(AppDestination.Traffic.route) {
                MonitoringScreen(
                    contentPadding,
                    hideAddresses = appearanceState.settings.hideAddresses,
                    viewModel = monitoringViewModel,
                )
            }
            composable(AppDestination.Usage.route) {
                UsageAnalyticsScreen(contentPadding, viewModel = usageViewModel)
            }
            composable(AppDestination.Operations.route) {
                SystemScreen(
                    contentPadding = contentPadding,
                    session = session,
                    profiles = sessionState.profiles,
                    onSwitchServer = onSwitchServer,
                    onDeleteServer = onDeleteServer,
                    onDisconnect = onDisconnect,
                    appearanceState = appearanceState,
                    viewModel = systemViewModel,
                )
            }
            composable(AppDestination.Settings.route) {
                SettingsScreen(
                    contentPadding = contentPadding,
                    appLockState = appLockState,
                    appearanceState = appearanceState,
                    onSetAppLockEnabled = onSetAppLockEnabled,
                    onSetAppLockTimeout = onSetAppLockTimeout,
                    onSetTheme = onSetTheme,
                    onSetLanguage = onSetLanguage,
                    onSetDynamicColor = onSetDynamicColor,
                    onSetAllowScreenshots = onSetAllowScreenshots,
                    onSetHideAddresses = onSetHideAddresses,
                )
            }
        }
    }
}
