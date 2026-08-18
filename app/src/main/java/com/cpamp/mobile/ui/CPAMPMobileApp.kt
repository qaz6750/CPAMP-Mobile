package com.cpamp.mobile.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
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
            if (destination != current) {
                navController.navigate(destination.route) {
                    popUpTo(AppDestination.Overview.route) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        },
    ) { contentPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Overview.route,
        ) {
            composable(
                route = AppDestination.Overview.route,
                enterTransition = { topLevelEnterTransition() },
                exitTransition = { topLevelExitTransition() },
                popEnterTransition = { topLevelEnterTransition() },
                popExitTransition = { topLevelExitTransition() },
            ) {
                DashboardScreen(
                    contentPadding = contentPadding,
                    hideAddresses = appearanceState.settings.hideAddresses,
                    viewModel = dashboardViewModel,
                )
            }
            composable(
                route = AppDestination.Accounts.route,
                enterTransition = { topLevelEnterTransition() },
                exitTransition = { topLevelExitTransition() },
                popEnterTransition = { topLevelEnterTransition() },
                popExitTransition = { topLevelExitTransition() },
            ) {
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
            composable(
                route = AppDestination.Usage.route,
                enterTransition = { topLevelEnterTransition() },
                exitTransition = { topLevelExitTransition() },
                popEnterTransition = { topLevelEnterTransition() },
                popExitTransition = { topLevelExitTransition() },
            ) {
                UsageAnalyticsScreen(
                    contentPadding = contentPadding,
                    viewModel = usageAnalyticsViewModel,
                )
            }
            composable(
                route = AppDestination.Settings.route,
                enterTransition = { topLevelEnterTransition() },
                exitTransition = { topLevelExitTransition() },
                popEnterTransition = { topLevelEnterTransition() },
                popExitTransition = { topLevelExitTransition() },
            ) {
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
private const val TOP_LEVEL_TRANSITION_DURATION_MILLIS = 220
private const val TOP_LEVEL_TRANSITION_OFFSET_DIVISOR = 12

private fun AnimatedContentTransitionScope<NavBackStackEntry>.topLevelEnterTransition(): EnterTransition? =
    topLevelSlideDirection()?.let { direction ->
        val fromRight = direction == AnimatedContentTransitionScope.SlideDirection.Left
        fadeIn(
            animationSpec = tween(
                durationMillis = TOP_LEVEL_TRANSITION_DURATION_MILLIS,
                easing = FastOutSlowInEasing,
            ),
        ) + slideInHorizontally(
            animationSpec = tween(
                durationMillis = TOP_LEVEL_TRANSITION_DURATION_MILLIS,
                easing = FastOutSlowInEasing,
            ),
            initialOffsetX = { fullWidth ->
                if (fromRight) fullWidth / TOP_LEVEL_TRANSITION_OFFSET_DIVISOR
                else -fullWidth / TOP_LEVEL_TRANSITION_OFFSET_DIVISOR
            },
        )
    }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.topLevelExitTransition(): ExitTransition? =
    topLevelSlideDirection()?.let { direction ->
        val exitsLeft = direction == AnimatedContentTransitionScope.SlideDirection.Left
        fadeOut(
            animationSpec = tween(
                durationMillis = TOP_LEVEL_TRANSITION_DURATION_MILLIS,
                easing = FastOutSlowInEasing,
            ),
        ) + slideOutHorizontally(
            animationSpec = tween(
                durationMillis = TOP_LEVEL_TRANSITION_DURATION_MILLIS,
                easing = FastOutSlowInEasing,
            ),
            targetOffsetX = { fullWidth ->
                if (exitsLeft) -fullWidth / TOP_LEVEL_TRANSITION_OFFSET_DIVISOR
                else fullWidth / TOP_LEVEL_TRANSITION_OFFSET_DIVISOR
            },
        )
    }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.topLevelSlideDirection():
    AnimatedContentTransitionScope.SlideDirection? {
    val initialIndex = topLevelDestinationIndex(initialState.destination.route) ?: return null
    val targetIndex = topLevelDestinationIndex(targetState.destination.route) ?: return null
    return when {
        targetIndex > initialIndex -> AnimatedContentTransitionScope.SlideDirection.Left
        targetIndex < initialIndex -> AnimatedContentTransitionScope.SlideDirection.Right
        else -> null
    }
}

private fun topLevelDestinationIndex(route: String?): Int? {
    val destination = when (route) {
        ACCOUNT_DETAIL_ROUTE -> AppDestination.Accounts
        APP_UPDATE_ROUTE -> AppDestination.Settings
        else -> AppDestination.entries.firstOrNull { it.route == route }
    } ?: return null
    return destination.ordinal
}

private fun accountDetailRoute(accountId: String): String {
    val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(accountId.toByteArray(Charsets.UTF_8))
    return "account/$encoded"
}

private fun String.decodeAccountId(): String = runCatching {
    String(Base64.getUrlDecoder().decode(this), Charsets.UTF_8)
}.getOrDefault("")
