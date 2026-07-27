package com.cpamp.mobile.ui.system

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cpamp.mobile.R
import com.cpamp.mobile.data.settings.AppLanguage
import com.cpamp.mobile.data.settings.AppTheme
import com.cpamp.mobile.domain.model.AuthenticatedSession
import com.cpamp.mobile.domain.model.ServerProfile
import com.cpamp.mobile.ui.components.AppBackground
import com.cpamp.mobile.ui.components.ConnectionPill
import com.cpamp.mobile.ui.components.PageHeader
import com.cpamp.mobile.ui.common.asTime
import com.cpamp.mobile.ui.security.AppLockUiState
import com.cpamp.mobile.ui.settings.AppearanceUiState

@Composable
fun SystemScreen(
    contentPadding: PaddingValues,
    session: AuthenticatedSession,
    profiles: List<ServerProfile>,
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
    viewModel: SystemViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmClearLogs by remember { mutableStateOf(false) }
    var deleteProfile by remember { mutableStateOf<ServerProfile?>(null) }

    AppBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = contentPadding.calculateTopPadding() + 24.dp,
                bottom = contentPadding.calculateBottomPadding() + 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                PageHeader(
                    eyebrow = stringResource(R.string.nav_system),
                    title = stringResource(R.string.system_title),
                    subtitle = stringResource(R.string.system_subtitle),
                    trailing = {
                        IconButton(onClick = viewModel::refresh, enabled = !state.loading) {
                            if (state.loading) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.refresh))
                            }
                        }
                    },
                )
            }
            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SystemTab.entries.forEach { tab ->
                        FilterChip(
                            selected = state.tab == tab,
                            onClick = { viewModel.selectTab(tab) },
                            label = { Text(stringResource(tab.labelResource)) },
                        )
                    }
                }
            }
            if (state.error != null || state.message != null) {
                item {
                    SystemNotice(
                        isError = state.error != null,
                        text = stringResource(
                            if (state.error != null) R.string.system_request_failed else R.string.logs_cleared,
                        ),
                        onDismiss = viewModel::clearNotice,
                    )
                }
            }
            when (state.tab) {
                SystemTab.Status -> {
                    item { ConnectionCard(session) }
                    item { ManagerStatusCard(state) }
                    item { CollectorStatusCard(state) }
                }
                SystemTab.Logs -> {
                    item {
                        OutlinedTextField(
                            value = state.logFilter,
                            onValueChange = viewModel::setLogFilter,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.filter_logs)) },
                            singleLine = true,
                        )
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            OutlinedButton(
                                onClick = { confirmClearLogs = true },
                                enabled = state.logs.isNotEmpty() && !state.mutating,
                            ) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                                Text(stringResource(R.string.clear_logs), modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                    if (state.visibleLogs.isEmpty()) {
                        item { EmptySystemCard(stringResource(R.string.no_logs)) }
                    } else {
                        items(state.visibleLogs) { line -> LogLine(line) }
                    }
                    if (state.nextCursor != null && state.logFilter.isBlank()) {
                        item {
                            OutlinedButton(
                                onClick = viewModel::loadMoreLogs,
                                enabled = !state.loadingMore,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (state.loadingMore) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                }
                                Text(stringResource(R.string.load_more), modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
                SystemTab.Servers -> {
                    items(profiles, key = ServerProfile::id) { profile ->
                        ServerCard(
                            profile = profile,
                            active = profile.id == session.profile.id,
                            onSwitch = { onSwitchServer(profile.id) },
                            onDelete = { deleteProfile = profile },
                        )
                    }
                    item {
                        Button(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.Logout, contentDescription = null)
                            Text(stringResource(R.string.disconnect), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
                SystemTab.Security -> {
                    item {
                        AppLockSettingsCard(
                            state = appLockState,
                            onSetEnabled = onSetAppLockEnabled,
                            onSetTimeout = onSetAppLockTimeout,
                        )
                    }
                    item { EmptySystemCard(stringResource(R.string.security_privacy_summary)) }
                }
                SystemTab.Appearance -> {
                    item {
                        AppearanceSettingsCard(
                            state = appearanceState,
                            onSetTheme = onSetTheme,
                            onSetLanguage = onSetLanguage,
                            onSetDynamicColor = onSetDynamicColor,
                        )
                    }
                }
            }
        }
    }

    if (confirmClearLogs) {
        ConfirmSystemAction(
            title = stringResource(R.string.clear_logs_title),
            message = stringResource(R.string.clear_logs_body),
            onDismiss = { confirmClearLogs = false },
            onConfirm = {
                confirmClearLogs = false
                viewModel.clearLogs()
            },
        )
    }
    deleteProfile?.let { profile ->
        ConfirmSystemAction(
            title = stringResource(R.string.delete_server_title),
            message = stringResource(R.string.delete_server_body, profile.name),
            onDismiss = { deleteProfile = null },
            onConfirm = {
                deleteProfile = null
                onDeleteServer(profile.id)
            },
        )
    }
}

@Composable
private fun ConnectionCard(session: AuthenticatedSession) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(session.profile.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                ConnectionPill(
                    label = stringResource(if (session.profile.usesCleartext) R.string.http_connection else R.string.https_connection),
                    secure = !session.profile.usesCleartext,
                )
            }
            Text(session.profile.baseUrl, style = MaterialTheme.typography.bodySmall)
            Text(
                stringResource(R.string.last_connected, session.profile.lastConnectedAt.asTime()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ManagerStatusCard(state: SystemUiState) {
    val status = state.status
    val info = state.info
    StatusCard(stringResource(R.string.system_status)) {
        StatusRow(stringResource(R.string.service_name), info?.service.orEmpty().ifBlank { stringResource(R.string.unknown_value) })
        StatusRow(stringResource(R.string.service_mode), info?.mode.orEmpty().ifBlank { stringResource(R.string.unknown_value) })
        StatusRow(stringResource(R.string.event_count_label), status?.events?.toString() ?: "—")
        StatusRow(stringResource(R.string.dead_letters), status?.deadLetters?.toString() ?: "—")
        StatusRow(
            stringResource(R.string.configuration_state),
            stringResource(if (info?.configured == true) R.string.configured else R.string.not_configured),
        )
    }
}

@Composable
private fun CollectorStatusCard(state: SystemUiState) {
    val collector = state.status?.collector
    StatusCard(stringResource(R.string.collector_status)) {
        StatusRow(
            stringResource(R.string.collector_running),
            stringResource(if (collector?.running == true) R.string.running else R.string.stopped),
        )
        StatusRow(stringResource(R.string.collector_mode), collector?.mode.orEmpty().ifBlank { "—" })
        if ((collector?.lastEventAt ?: 0) > 0) {
            StatusRow(stringResource(R.string.last_event), requireNotNull(collector).lastEventAt.asTime())
        }
        if (!collector?.error.isNullOrBlank()) {
            Text(
                collector?.error.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun StatusCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LogLine(line: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))) {
        Text(
            line,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun ServerCard(
    profile: ServerProfile,
    active: Boolean,
    onSwitch: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))) {
        Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(profile.name, fontWeight = FontWeight.SemiBold)
                    Text(profile.baseUrl, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                ConnectionPill(
                    label = stringResource(
                        when {
                            active -> R.string.active_server
                            profile.usesCleartext -> R.string.http_connection
                            else -> R.string.https_connection
                        },
                    ),
                    secure = !profile.usesCleartext,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (!active) {
                    TextButton(onClick = onSwitch) { Text(stringResource(R.string.switch_server)) }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.delete))
                }
            }
        }
    }
}

@Composable
private fun AppLockSettingsCard(
    state: AppLockUiState,
    onSetEnabled: (Boolean) -> Unit,
    onSetTimeout: (Int) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.app_lock), fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.app_lock_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.mutating) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Switch(
                        checked = state.enabled,
                        onCheckedChange = onSetEnabled,
                    )
                }
            }
            if (state.enabled) {
                Text(stringResource(R.string.lock_after), style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(1, 5, 15, 60).forEach { minutes ->
                        FilterChip(
                            selected = state.timeoutMinutes == minutes,
                            onClick = { onSetTimeout(minutes) },
                            label = {
                                Text(
                                    if (minutes == 60) {
                                        stringResource(R.string.one_hour)
                                    } else {
                                        stringResource(R.string.minutes_value, minutes)
                                    },
                                )
                            },
                        )
                    }
                }
            }
            if (state.error) {
                Text(
                    stringResource(R.string.security_change_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun AppearanceSettingsCard(
    state: AppearanceUiState,
    onSetTheme: (AppTheme) -> Unit,
    onSetLanguage: (AppLanguage) -> Unit,
    onSetDynamicColor: (Boolean) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppLanguage.entries.forEach { language ->
                    FilterChip(
                        selected = state.settings.language == language,
                        onClick = { onSetLanguage(language) },
                        label = { Text(stringResource(language.labelResource)) },
                    )
                }
            }
            Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppTheme.entries.forEach { theme ->
                    FilterChip(
                        selected = state.settings.theme == theme,
                        onClick = { onSetTheme(theme) },
                        label = { Text(stringResource(theme.labelResource)) },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.dynamic_color), fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.dynamic_color_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.settings.dynamicColor,
                    onCheckedChange = onSetDynamicColor,
                )
            }
        }
    }
}

@Composable
private fun EmptySystemCard(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))) {
        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SystemNotice(isError: Boolean, text: String, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) }
        }
    }
}

@Composable
private fun ConfirmSystemAction(title: String, message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { Button(onClick = onConfirm) { Text(stringResource(R.string.confirm_change)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private val SystemTab.labelResource: Int
    get() = when (this) {
        SystemTab.Status -> R.string.system_status
        SystemTab.Logs -> R.string.system_logs
        SystemTab.Servers -> R.string.system_servers
        SystemTab.Security -> R.string.system_security
        SystemTab.Appearance -> R.string.system_appearance
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