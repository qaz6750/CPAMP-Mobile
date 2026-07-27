package com.cpamp.mobile.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cpamp.mobile.R
import com.cpamp.mobile.data.settings.AppLanguage
import com.cpamp.mobile.data.settings.AppTheme
import com.cpamp.mobile.ui.components.AppBackground
import com.cpamp.mobile.ui.security.AppLockUiState

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    appLockState: AppLockUiState,
    appearanceState: AppearanceUiState,
    onBack: () -> Unit,
    onSetAppLockEnabled: (Boolean) -> Unit,
    onSetAppLockTimeout: (Int) -> Unit,
    onSetTheme: (AppTheme) -> Unit,
    onSetLanguage: (AppLanguage) -> Unit,
    onSetDynamicColor: (Boolean) -> Unit,
    onSetAllowScreenshots: (Boolean) -> Unit,
    onSetHideAddresses: (Boolean) -> Unit,
) {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                    Column(Modifier.padding(start = 6.dp)) {
                        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.settings_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))) {
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