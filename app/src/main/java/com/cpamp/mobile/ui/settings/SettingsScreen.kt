package com.cpamp.mobile.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cpamp.mobile.R
import com.cpamp.mobile.data.settings.AppLanguage
import com.cpamp.mobile.data.settings.AppTheme
import com.cpamp.mobile.domain.model.AuthenticatedSession
import com.cpamp.mobile.domain.model.ServerProfile
import com.cpamp.mobile.ui.components.AppBackground
import com.cpamp.mobile.ui.components.AppCard
import com.cpamp.mobile.ui.components.PageHeader
import com.cpamp.mobile.ui.security.AppLockUiState


@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    session: AuthenticatedSession,
    profiles: List<ServerProfile>,
    appLockState: AppLockUiState,
    appearanceState: AppearanceUiState,
    onSetAppLockEnabled: (Boolean) -> Unit,
    onSetAppLockTimeout: (Int) -> Unit,
    onSetTheme: (AppTheme) -> Unit,
    onSetLanguage: (AppLanguage) -> Unit,
    onSetAllowScreenshots: (Boolean) -> Unit,
    onSetHideAddresses: (Boolean) -> Unit,
    onSwitchServer: (String) -> Unit,
    onDeleteServer: (String) -> Unit,
    onDisconnect: () -> Unit,
    onOpenUpdates: () -> Unit,
    onOpenSourceLicenses: () -> Unit,
    updateViewModel: AppUpdateViewModel = hiltViewModel(),
    cacheViewModel: CacheCleanupViewModel = hiltViewModel(),
) {
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
    val cacheState by cacheViewModel.state.collectAsStateWithLifecycle()
    var confirmClearCache by rememberSaveable { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) updateViewModel.refreshDownloadStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    AppBackground {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 380.dp),
            modifier = Modifier.align(Alignment.TopCenter).fillMaxHeight()
                .widthIn(max = 1100.dp).fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = contentPadding.calculateTopPadding() + 18.dp,
                bottom = contentPadding.calculateBottomPadding() + 28.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                PageHeader(
                    eyebrow = stringResource(R.string.nav_settings),
                    title = stringResource(R.string.settings_title),
                    subtitle = stringResource(R.string.settings_subtitle),
                )
            }
            if (appearanceState.error) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    AppCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
                        Text(
                            stringResource(R.string.settings_change_failed),
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                ServerManagementSettings(
                    session = session,
                    profiles = profiles,
                    hideAddresses = appearanceState.settings.hideAddresses,
                    onSwitchServer = onSwitchServer,
                    onDeleteServer = onDeleteServer,
                    onDisconnect = onDisconnect,
                )
            }
            item {
                SettingsGroupCard(stringResource(R.string.system_security)) {
                    SettingSwitchRow(
                        title = stringResource(R.string.app_lock),
                        help = stringResource(R.string.app_lock_help),
                        checked = appLockState.enabled,
                        enabled = !appLockState.mutating,
                        loading = appLockState.mutating,
                        onCheckedChange = onSetAppLockEnabled,
                    )
                    if (appLockState.enabled) {
                        SettingsDivider()
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(stringResource(R.string.lock_after), style = MaterialTheme.typography.labelLarge)
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                listOf(1, 5, 15, 60).forEach { minutes ->
                                    FilterChip(
                                        selected = appLockState.timeoutMinutes == minutes,
                                        onClick = { onSetAppLockTimeout(minutes) },
                                        label = {
                                            Text(
                                                if (minutes == 60) stringResource(R.string.one_hour)
                                                else stringResource(R.string.minutes_value, minutes),
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                    SettingsDivider()
                    SettingSwitchRow(
                        title = stringResource(R.string.allow_screenshots),
                        help = stringResource(R.string.allow_screenshots_help),
                        checked = appearanceState.settings.allowScreenshots,
                        onCheckedChange = onSetAllowScreenshots,
                    )
                    SettingsDivider()
                    SettingSwitchRow(
                        title = stringResource(R.string.hide_addresses),
                        help = stringResource(R.string.hide_addresses_help),
                        checked = appearanceState.settings.hideAddresses,
                        onCheckedChange = onSetHideAddresses,
                    )
                    if (appLockState.error) {
                        SettingsDivider()
                        Text(
                            stringResource(R.string.security_change_failed),
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            item {
                SettingsGroupCard(stringResource(R.string.system_appearance)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(stringResource(R.string.language), fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AppLanguage.entries.forEach { language ->
                                FilterChip(
                                    selected = appearanceState.settings.language == language,
                                    onClick = { onSetLanguage(language) },
                                    label = { Text(stringResource(language.labelResource)) },
                                )
                            }
                        }
                    }
                    SettingsDivider()
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(stringResource(R.string.theme), fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AppTheme.entries.forEach { theme ->
                                FilterChip(
                                    selected = appearanceState.settings.theme == theme,
                                    onClick = { onSetTheme(theme) },
                                    label = { Text(stringResource(theme.labelResource)) },
                                )
                            }
                        }
                    }
                }
            }
            item {
                SettingsGroupCard(stringResource(R.string.storage_cache)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            stringResource(R.string.cache_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = { confirmClearCache = true },
                            enabled = !cacheState.clearing,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (cacheState.clearing) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Outlined.DeleteSweep, contentDescription = null)
                            }
                            Text(
                                stringResource(
                                    if (cacheState.clearing) R.string.clearing_cache else R.string.clear_cache,
                                ),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                        cacheState.result?.let { result ->
                            Text(
                                stringResource(
                                    if (result == CacheCleanupResult.Success) R.string.cache_cleared
                                    else R.string.cache_clear_failed,
                                ),
                                color = if (result == CacheCleanupResult.Success) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            item {
                UpdateSettingsCard(
                    state = updateState,
                    onCheckForUpdates = updateViewModel::checkForUpdates,
                    onDownloadUpdate = updateViewModel::downloadUpdate,
                    onShowUpdate = onOpenUpdates,
                    onOpenSourceLicenses = onOpenSourceLicenses,
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                AppCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(
                        stringResource(R.string.security_privacy_summary),
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    if (confirmClearCache) {
        AlertDialog(
            onDismissRequest = { confirmClearCache = false },
            title = { Text(stringResource(R.string.clear_cache_title)) },
            text = { Text(stringResource(R.string.clear_cache_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearCache = false
                        cacheViewModel.clearResult()
                        cacheViewModel.clear()
                    },
                ) {
                    Text(stringResource(R.string.clear_cache))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearCache = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@get:StringRes
private val AppLanguage.labelResource: Int
    get() = when (this) {
        AppLanguage.System -> R.string.follow_system
        AppLanguage.SimplifiedChinese -> R.string.simplified_chinese
        AppLanguage.English -> R.string.english
    }

@get:StringRes
private val AppTheme.labelResource: Int
    get() = when (this) {
        AppTheme.System -> R.string.follow_system
        AppTheme.Light -> R.string.light_theme
        AppTheme.Dark -> R.string.dark_theme
    }
