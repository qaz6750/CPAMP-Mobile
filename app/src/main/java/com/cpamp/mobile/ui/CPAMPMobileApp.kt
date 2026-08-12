package com.cpamp.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cpamp.mobile.data.settings.AppLanguage
import com.cpamp.mobile.data.settings.AppTheme
import com.cpamp.mobile.ui.accounts.AccountDetailScreen
import com.cpamp.mobile.ui.accounts.AccountsScreen
import com.cpamp.mobile.ui.accounts.AccountsViewModel
import com.cpamp.mobile.ui.auth.LoginScreen
import com.cpamp.mobile.ui.auth.SessionLoadingScreen
import com.cpamp.mobile.ui.auth.SessionUiState
import com.cpamp.mobile.ui.auth.SessionViewModel
import com.cpamp.mobile.ui.dashboard.DashboardScreen
import com.cpamp.mobile.ui.dashboard.DashboardViewModel
import com.cpamp.mobile.ui.navigation.ACCOUNT_DETAIL_ROUTE
import com.cpamp.mobile.ui.navigation.AppDestination
import com.cpamp.mobile.ui.navigation.MainNavigationScaffold
import com.cpamp.mobile.ui.security.AppLockUiState
import com.cpamp.mobile.ui.settings.AppearanceUiState
import com.cpamp.mobile.ui.settings.AppUpdateScreen
import com.cpamp.mobile.ui.settings.SettingsScreen
import com.cpamp.mobile.ui.usage.UsageAnalyticsScreen
import com.cpamp.mobile.ui.usage.UsageAnalyticsViewModel
import java.util.Base64

@Composable
fun CPAMPMobileApp(
    appLockState: AppLockUiState,
    onSetAppLockEnabled: (Boolean) -> Unit,
    onSetAppLockTimeout: (Int) -> Unit,
    appearanceState: AppearanceUiState,
    onSetTheme: (AppTheme) -> Unit,
    onSetLanguage: (AppLanguage) -> Unit,
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
            onSetAllowScreenshots = onSetAllowScreenshots,
            onSetHideAddresses = onSetHideAddresses,
        )
    }
}

@Composable
private fun ConnectedApp(
    sessionState: SessionUiState,
    onSwitchServer: (String) -> Unit,
    onDeleteServer: (String) -> Unit,
    onDisconnect: () -> Unit,
    appLockState: AppLockUiState,
    onSetAppLockEnabled: (Boolean) -> Unit,
    onSetAppLockTimeout: (Int) -> Unit,
    appearanceState: AppearanceUiState,
    onSetTheme: (AppTheme) -> Unit,
    onSetLanguage: (AppLanguage) -> Unit,
    onSetAllowScreenshots: (Boolean) -> Unit,
    onSetHideAddresses: (Boolean) -> Unit,
) {
    val session = requireNotNull(sessionState.session)
    val dashboardViewModel: DashboardViewModel = hiltViewModel()
    val accountsViewModel: AccountsViewModel = hiltViewModel()
    val usageAnalyticsViewModel: UsageAnalyticsViewModel = hiltViewModel()
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
            composable(AppDestination.Overview.route) {
                DashboardScreen(
                    contentPadding = contentPadding,
                    hideAddresses = appearanceState.settings.hideAddresses,
                    viewModel = dashboardViewModel,
                )
            }
            composable(AppDestination.Accounts.route) {
                AccountsScreen(
                    contentPadding = contentPadding,
                    hideAddresses = appearanceState.settings.hideAddresses,
                    onOpenAccount = { accountId -> navController.navigate(accountDetailRoute(accountId)) },
                    viewModel = accountsViewModel,
                )
            }
            composable(ACCOUNT_DETAIL_ROUTE) { entry ->
                AccountDetailScreen(
                    contentPadding = contentPadding,
                    accountId = entry.arguments?.getString("accountId").orEmpty().decodeAccountId(),
                    onBack = navController::popBackStack,
                    viewModel = accountsViewModel,
                )
            }
            composable(AppDestination.Usage.route) {
                UsageAnalyticsScreen(
                    contentPadding = contentPadding,
                    viewModel = usageAnalyticsViewModel,
                )
            }
            composable(AppDestination.Settings.route) {
                SettingsScreen(
                    contentPadding = contentPadding,
                    session = session,
                    profiles = sessionState.profiles,
                    appLockState = appLockState,
                    appearanceState = appearanceState,
                    onSetAppLockEnabled = onSetAppLockEnabled,
                    onSetAppLockTimeout = onSetAppLockTimeout,
                    onSetTheme = onSetTheme,
                    onSetLanguage = onSetLanguage,
                    onSetAllowScreenshots = onSetAllowScreenshots,
                    onSetHideAddresses = onSetHideAddresses,
                    onSwitchServer = onSwitchServer,
                    onDeleteServer = onDeleteServer,
                    onDisconnect = onDisconnect,
                    onOpenUpdates = { navController.navigate(APP_UPDATE_ROUTE) },
                )
            }
            composable(APP_UPDATE_ROUTE) {
                AppUpdateScreen(
                    contentPadding = contentPadding,
                    onBack = navController::popBackStack,
                )
            }
        }
    }
}

private const val APP_UPDATE_ROUTE = "app-update"

private fun accountDetailRoute(accountId: String): String {
    val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(accountId.toByteArray(Charsets.UTF_8))
    return "account/$encoded"
}

private fun String.decodeAccountId(): String = runCatching {
    String(Base64.getUrlDecoder().decode(this), Charsets.UTF_8)
}.getOrDefault("")
