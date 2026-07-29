package com.cpamp.mobile.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cpamp.mobile.R
import com.cpamp.mobile.BuildConfig
import com.cpamp.mobile.data.settings.AppLanguage
import com.cpamp.mobile.data.settings.AppTheme
import com.cpamp.mobile.data.update.AppUpdateState
import com.cpamp.mobile.data.update.UpdateError
import com.cpamp.mobile.data.update.UpdateStatus
import com.cpamp.mobile.data.update.displayBody
import com.cpamp.mobile.ui.components.AppBackground
import com.cpamp.mobile.ui.security.AppLockUiState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    appLockState: AppLockUiState,
    appearanceState: AppearanceUiState,
    onSetAppLockEnabled: (Boolean) -> Unit,
    onSetAppLockTimeout: (Int) -> Unit,
    onSetTheme: (AppTheme) -> Unit,
    onSetLanguage: (AppLanguage) -> Unit,
    onSetDynamicColor: (Boolean) -> Unit,
    onSetAllowScreenshots: (Boolean) -> Unit,
    onSetHideAddresses: (Boolean) -> Unit,
    updateViewModel: AppUpdateViewModel = hiltViewModel(),
    cacheViewModel: CacheCleanupViewModel = hiltViewModel(),
) {
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
    val cacheState by cacheViewModel.state.collectAsStateWithLifecycle()
    var showUpstreamLicense by rememberSaveable { mutableStateOf(false) }
    var confirmClearCache by rememberSaveable { mutableStateOf(false) }
    var showUpdateDetails by rememberSaveable { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) updateViewModel.refreshDownloadStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(updateState.status, updateState.release?.tagName) {
        if (updateState.status == UpdateStatus.Available) showUpdateDetails = true
    }
    LaunchedEffect(updateState.status) {
        while (isActive && updateState.status == UpdateStatus.Downloading) {
            delay(750)
            updateViewModel.refreshDownloadStatus()
        }
    }
    AppBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = contentPadding.calculateTopPadding() + 18.dp,
                bottom = contentPadding.calculateBottomPadding() + 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column {
                    Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.settings_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                SettingsCard(stringResource(R.string.system_security)) {
                    SettingSwitchRow(
                        title = stringResource(R.string.app_lock),
                        help = stringResource(R.string.app_lock_help),
                        checked = appLockState.enabled,
                        enabled = !appLockState.mutating,
                        loading = appLockState.mutating,
                        onCheckedChange = onSetAppLockEnabled,
                    )
                    if (appLockState.enabled) {
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
                    SettingSwitchRow(
                        title = stringResource(R.string.allow_screenshots),
                        help = stringResource(R.string.allow_screenshots_help),
                        checked = appearanceState.settings.allowScreenshots,
                        onCheckedChange = onSetAllowScreenshots,
                    )
                    SettingSwitchRow(
                        title = stringResource(R.string.hide_addresses),
                        help = stringResource(R.string.hide_addresses_help),
                        checked = appearanceState.settings.hideAddresses,
                        onCheckedChange = onSetHideAddresses,
                    )
                    if (appLockState.error) {
                        Text(stringResource(R.string.security_change_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                SettingsCard(stringResource(R.string.system_appearance)) {
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
                    SettingSwitchRow(
                        title = stringResource(R.string.dynamic_color),
                        help = stringResource(R.string.dynamic_color_help),
                        checked = appearanceState.settings.dynamicColor,
                        onCheckedChange = onSetDynamicColor,
                    )
                }
            }
            item {
                SettingsCard(stringResource(R.string.storage_cache)) {
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
            item {
                UpdateSettingsCard(
                    state = updateState,
                    onCheck = updateViewModel::checkForUpdates,
                    onShowUpdate = { showUpdateDetails = true },
                    onOpenSourceLicenses = { showUpstreamLicense = true },
                )
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))) {
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
    if (showUpstreamLicense) {
        val context = LocalContext.current
        val upstreamLicense = remember(context) {
            context.resources.openRawResource(R.raw.cpa_manager_plus_license)
                .bufferedReader().use { it.readText() }
        }
        AlertDialog(
            onDismissRequest = { showUpstreamLicense = false },
            title = { Text(stringResource(R.string.open_source_licenses)) },
            text = {
                Text(
                    upstreamLicense,
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(onClick = { showUpstreamLicense = false }) {
                    Text(stringResource(R.string.dismiss))
                }
            },
        )
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
    if (showUpdateDetails && updateState.release != null) {
        UpdateDetailsDialog(
            state = updateState,
            onDismiss = { showUpdateDetails = false },
            onDownload = updateViewModel::downloadUpdate,
        )
    }
}

@Composable
private fun UpdateSettingsCard(
    state: AppUpdateState,
    onCheck: () -> Unit,
    onShowUpdate: () -> Unit,
    onOpenSourceLicenses: () -> Unit,
) {
    val context = LocalContext.current
    SettingsCard(stringResource(R.string.about_updates)) {
        Text(
            stringResource(R.string.current_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodyMedium,
        )
        state.release?.let { release ->
            Text(
                stringResource(R.string.latest_version, release.tagName.removePrefix("v")),
                fontWeight = FontWeight.SemiBold,
            )
            release.publishedAt?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Text(updateStatusText(state), style = MaterialTheme.typography.bodySmall, color = statusColor(state))
        when (state.status) {
            UpdateStatus.Available, UpdateStatus.Downloading, UpdateStatus.Verifying -> Button(
                onClick = onShowUpdate,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Download, contentDescription = null)
                Text(stringResource(R.string.view_update_details), modifier = Modifier.padding(start = 8.dp))
            }
            UpdateStatus.ReadyToInstall -> Button(
                onClick = { state.installUri?.let { context.installUpdate(it) } },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.InstallMobile, contentDescription = null)
                Text(stringResource(R.string.install_update), modifier = Modifier.padding(start = 8.dp))
            }
            else -> Button(
                onClick = onCheck,
                enabled = state.status !in setOf(UpdateStatus.Checking, UpdateStatus.Downloading, UpdateStatus.Verifying),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.status == UpdateStatus.Checking) {
                    CircularProgressIndicator(Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                }
                Text(stringResource(R.string.check_for_updates), modifier = Modifier.padding(start = 8.dp))
            }
        }
        TextButton(onClick = onOpenSourceLicenses, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.open_source_licenses))
        }
    }
}

@Composable
private fun UpdateDetailsDialog(
    state: AppUpdateState,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
) {
    val context = LocalContext.current
    val language = LocalConfiguration.current.locales[0].language
    val release = state.release ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.update_details_title, release.tagName.removePrefix("v")))
                release.publishedAt?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                release.displayBody(language)?.let { body ->
                    Text(
                        body,
                        modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (state.status in setOf(UpdateStatus.Downloading, UpdateStatus.Verifying)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(updateStatusText(state), style = MaterialTheme.typography.bodySmall)
                        state.progressPercent?.let { progress ->
                            LinearProgressIndicator(
                                progress = { progress / 100f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } ?: LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {
            when (state.status) {
                UpdateStatus.Available -> Button(onClick = onDownload) {
                    Icon(Icons.Outlined.Download, contentDescription = null)
                    Text(stringResource(R.string.download_update), modifier = Modifier.padding(start = 8.dp))
                }
                UpdateStatus.ReadyToInstall -> Button(
                    onClick = { state.installUri?.let(context::installUpdate) },
                ) {
                    Icon(Icons.Outlined.InstallMobile, contentDescription = null)
                    Text(stringResource(R.string.install_update), modifier = Modifier.padding(start = 8.dp))
                }
                else -> Unit
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun updateStatusText(state: AppUpdateState): String = when (state.status) {
    UpdateStatus.Idle -> stringResource(R.string.update_manual_check)
    UpdateStatus.Checking -> stringResource(R.string.update_checking)
    UpdateStatus.NoRelease -> stringResource(R.string.update_no_release)
    UpdateStatus.UpToDate -> stringResource(R.string.update_up_to_date)
    UpdateStatus.Available -> stringResource(R.string.update_available)
    UpdateStatus.Downloading -> state.progressPercent?.let { stringResource(R.string.update_downloading_percent, it) }
        ?: stringResource(R.string.update_downloading)
    UpdateStatus.Verifying -> stringResource(R.string.update_verifying)
    UpdateStatus.ReadyToInstall -> stringResource(R.string.update_ready)
    UpdateStatus.Failed -> stringResource(
        when (state.error) {
            UpdateError.RateLimited -> R.string.update_rate_limited
            UpdateError.InvalidRelease -> R.string.update_invalid_release
            UpdateError.Checksum -> R.string.update_checksum_failed
            UpdateError.Signature -> R.string.update_signature_failed
            else -> R.string.update_failed
        },
    )
}

@Composable
private fun statusColor(state: AppUpdateState) = when (state.status) {
    UpdateStatus.Failed -> MaterialTheme.colorScheme.error
    UpdateStatus.Available, UpdateStatus.ReadyToInstall -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun android.content.Context.installUpdate(uri: Uri) {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
        !packageManager.canRequestPackageInstalls()
    ) {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:$packageName"),
            ),
        )
        return
    }
    startActivity(
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        },
    )
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    help: String,
    checked: Boolean,
    enabled: Boolean = true,
    loading: Boolean = false,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (loading) {
            CircularProgressIndicator(Modifier.padding(8.dp), strokeWidth = 2.dp)
        } else {
            Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
        }
    }
}

private val AppLanguage.labelResource: Int
    get() = when (this) {
        AppLanguage.System -> R.string.follow_system
        AppLanguage.SimplifiedChinese -> R.string.simplified_chinese
        AppLanguage.English -> R.string.english
    }

private val AppTheme.labelResource: Int
    get() = when (this) {
        AppTheme.System -> R.string.follow_system
        AppTheme.Light -> R.string.light_theme
        AppTheme.Dark -> R.string.dark_theme
    }
