package com.cpamp.mobile.ui.system

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cpamp.mobile.R
import com.cpamp.mobile.domain.model.AuthenticatedSession
import com.cpamp.mobile.ui.components.AppBackground
import com.cpamp.mobile.ui.components.AppCard
import com.cpamp.mobile.ui.components.CategoryListRow
import com.cpamp.mobile.ui.components.LoadingIconButton
import com.cpamp.mobile.ui.components.PageHeader
import com.cpamp.mobile.ui.settings.AppearanceUiState

@Composable
fun SystemScreen(
    contentPadding: PaddingValues,
    session: AuthenticatedSession,
    appearanceState: AppearanceUiState,
    viewModel: SystemViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmClearLogs by rememberSaveable { mutableStateOf(false) }

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
                    eyebrow = stringResource(R.string.nav_operations),
                    title = stringResource(R.string.system_title),
                    subtitle = stringResource(R.string.system_subtitle),
                    trailing = {
                        LoadingIconButton(
                            icon = Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.refresh),
                            loading = state.loading,
                            onClick = viewModel::refresh,
                        )
                    },
                )
            }
            item {
                Column {
                    SystemTab.entries.forEach { tab ->
                        CategoryListRow(
                            title = stringResource(tab.labelResource),
                            supporting = tab.supportingText(state),
                            icon = tab.icon,
                            selected = state.tab == tab,
                            onClick = { viewModel.selectTab(tab) },
                        )
                    }
                }
            }
            state.notice?.let { notice ->
                item {
                    SystemNoticeCard(
                        isError = notice == SystemNotice.RequestFailed,
                        text = stringResource(
                            when (notice) {
                                SystemNotice.RequestFailed -> R.string.system_request_failed
                                SystemNotice.LogsCleared -> R.string.logs_cleared
                            },
                        ),
                        onDismiss = viewModel::clearNotice,
                    )
                }
            }
            when (state.tab) {
                SystemTab.Status -> {
                    item { ConnectionCard(session, appearanceState.settings.hideAddresses) }
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
}

@Composable
private fun LogLine(line: RuntimeLogEntry) {
    var expanded by rememberSaveable(line.raw) { mutableStateOf(false) }
    val levelColor = when (line.level) {
        RuntimeLogLevel.Error -> MaterialTheme.colorScheme.error
        RuntimeLogLevel.Warning -> WarningLogColor
        RuntimeLogLevel.Info -> MaterialTheme.colorScheme.primary
        RuntimeLogLevel.Debug, RuntimeLogLevel.Trace, RuntimeLogLevel.Unknown -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    AppCard(modifier = Modifier.clickable { expanded = !expanded }) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    line.timestamp ?: stringResource(R.string.log_time_unknown),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(line.level.displayLabel(), style = MaterialTheme.typography.labelSmall, color = levelColor)
            }
            Text(
                if (expanded) line.raw else line.message,
                style = MaterialTheme.typography.bodySmall,
                color = if (line.level in setOf(RuntimeLogLevel.Error, RuntimeLogLevel.Warning)) levelColor
                    else MaterialTheme.colorScheme.onSurface,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RuntimeLogLevel.displayLabel(): String = stringResource(
    when (this) {
        RuntimeLogLevel.Error -> R.string.log_level_error
        RuntimeLogLevel.Warning -> R.string.log_level_warning
        RuntimeLogLevel.Info -> R.string.log_level_info
        RuntimeLogLevel.Debug -> R.string.log_level_debug
        RuntimeLogLevel.Trace -> R.string.log_level_trace
        RuntimeLogLevel.Unknown -> R.string.log_level_unknown
    },
)

private val WarningLogColor = androidx.compose.ui.graphics.Color(0xFFD98200)

@Composable
private fun EmptySystemCard(text: String) {
    AppCard(containerColor = MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SystemNoticeCard(isError: Boolean, text: String, onDismiss: () -> Unit) {
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

@get:StringRes
private val SystemTab.labelResource: Int
    get() = when (this) {
        SystemTab.Status -> R.string.system_status
        SystemTab.Logs -> R.string.system_logs
    }

private val SystemTab.icon: ImageVector
    get() = when (this) {
        SystemTab.Status -> Icons.Outlined.MonitorHeart
        SystemTab.Logs -> Icons.AutoMirrored.Outlined.ListAlt
    }

@Composable
private fun SystemTab.supportingText(state: SystemUiState): String = when (this) {
    SystemTab.Status -> stringResource(if (state.status == null) R.string.manual_refresh_required else R.string.system_data_loaded)
    SystemTab.Logs -> stringResource(R.string.item_count, state.logs.size)
}
