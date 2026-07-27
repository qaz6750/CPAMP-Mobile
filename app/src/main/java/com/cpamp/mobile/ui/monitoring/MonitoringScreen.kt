package com.cpamp.mobile.ui.monitoring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Toll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cpamp.mobile.R
import com.cpamp.mobile.data.remote.model.RequestEventDto
import com.cpamp.mobile.ui.common.LifecyclePollingEffect
import com.cpamp.mobile.ui.common.SensitiveText
import com.cpamp.mobile.ui.common.asPercent
import com.cpamp.mobile.ui.common.asTime
import com.cpamp.mobile.ui.common.compactNumber
import com.cpamp.mobile.ui.components.AppBackground
import com.cpamp.mobile.ui.components.PageHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitoringScreen(
    contentPadding: PaddingValues,
    viewModel: MonitoringViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedEvent by remember { mutableStateOf<RequestEventDto?>(null) }
    LifecyclePollingEffect(viewModel::setActive)

    AppBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = contentPadding.calculateTopPadding() + 24.dp,
                bottom = contentPadding.calculateBottomPadding() + 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                PageHeader(
                    eyebrow = state.profile?.name ?: stringResource(R.string.nav_traffic),
                    title = stringResource(R.string.traffic_title),
                    subtitle = stringResource(R.string.traffic_subtitle),
                    trailing = {
                        IconButton(onClick = viewModel::refresh, enabled = !state.refreshing) {
                            if (state.refreshing) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.refresh))
                            }
                        }
                    },
                )
            }
            item {
                OutlinedTextField(
                    value = state.filter.search,
                    onValueChange = viewModel::setSearch,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    label = { Text(stringResource(R.string.search_requests)) },
                    placeholder = { Text(stringResource(R.string.search_requests_hint)) },
                    singleLine = true,
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TrafficWindow.entries.forEach { window ->
                            FilterChip(
                                selected = state.filter.window == window,
                                onClick = { viewModel.setWindow(window) },
                                label = { Text(stringResource(window.labelResource())) },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !state.filter.failedOnly,
                            onClick = { viewModel.setFailedOnly(false) },
                            label = { Text(stringResource(R.string.all_requests)) },
                        )
                        FilterChip(
                            selected = state.filter.failedOnly,
                            onClick = { viewModel.setFailedOnly(true) },
                            label = { Text(stringResource(R.string.failed_only)) },
                            leadingIcon = { Icon(Icons.Outlined.ErrorOutline, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        )
                    }
                }
            }
            if (state.fromCache || state.error != null) {
                item { MonitoringNotice(state) }
            }
            if (state.loading && state.response == null) {
                item {
                    Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            state.response?.summary?.let { summary ->
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CompactMetric(
                            label = stringResource(R.string.metric_requests),
                            value = summary.totalCalls.compactNumber(),
                            icon = Icons.Outlined.Toll,
                            modifier = Modifier.weight(1f),
                        )
                        CompactMetric(
                            label = stringResource(R.string.metric_success),
                            value = summary.successRate.asPercent(),
                            icon = Icons.Outlined.CheckCircle,
                            modifier = Modifier.weight(1f),
                        )
                        CompactMetric(
                            label = stringResource(R.string.p95_latency),
                            value = summary.p95LatencyMs?.let { "%.0f ms".format(it) } ?: "—",
                            icon = Icons.Outlined.Speed,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            val events = state.response?.events?.items.orEmpty()
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.request_events),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    state.response?.events?.let { page ->
                        Text(
                            stringResource(R.string.event_count, page.totalCount),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (events.isEmpty() && !state.loading) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))) {
                        Column(
                            Modifier.fillMaxWidth().padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Outlined.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.no_matching_requests), fontWeight = FontWeight.SemiBold)
                            Text(
                                stringResource(R.string.adjust_filters),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                items(events, key = RequestEventDto::stableId) { event ->
                    RequestEventCard(event = event, onClick = { selectedEvent = event })
                }
            }
        }
    }

    selectedEvent?.let { event ->
        ModalBottomSheet(onDismissRequest = { selectedEvent = null }) {
            RequestEventDetails(event)
        }
    }
}

@Composable
private fun MonitoringNotice(state: MonitoringUiState) {
    val message = when {
        state.error == MonitoringError.Unauthorized -> stringResource(R.string.dashboard_unauthorized)
        state.error == MonitoringError.RateLimited -> stringResource(R.string.monitoring_rate_limited)
        state.error == MonitoringError.Timeout -> stringResource(R.string.monitoring_timeout)
        state.error != null -> stringResource(R.string.monitoring_offline)
        state.fromCache -> stringResource(R.string.cached_data_notice, state.updatedAt?.asTime().orEmpty())
        else -> ""
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Text(message, modifier = Modifier.fillMaxWidth().padding(14.dp), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CompactMetric(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RequestEventCard(event: RequestEventDto, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(10.dp),
                shape = CircleShape,
                color = if (event.failed) MaterialTheme.colorScheme.error else SUCCESS_COLOR,
            ) {}
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        event.model.ifBlank { stringResource(R.string.unknown_model) },
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(event.timestampMs.asTime(), style = MaterialTheme.typography.labelSmall)
                }
                Text(
                    event.endpoint.ifBlank { event.path.ifBlank { "—" } },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(event.totalTokens.compactNumber() + " tok", style = MaterialTheme.typography.labelSmall)
                    event.latencyMs?.let { Text("$it ms", style = MaterialTheme.typography.labelSmall) }
                    event.failStatusCode?.let { Text("HTTP $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

@Composable
private fun RequestEventDetails(event: RequestEventDto) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(stringResource(R.string.request_details), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(event.timestampMs.asTime(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { DetailRow(stringResource(R.string.detail_status), if (event.failed) stringResource(R.string.failed) else stringResource(R.string.succeeded)) }
        item { DetailRow(stringResource(R.string.detail_model), event.model.ifBlank { "—" }) }
        item { DetailRow(stringResource(R.string.detail_endpoint), event.endpoint.ifBlank { event.path.ifBlank { "—" } }) }
        item { DetailRow(stringResource(R.string.detail_provider), event.authProviderSnapshot.ifBlank { event.source.ifBlank { "—" } }) }
        item { DetailRow(stringResource(R.string.detail_account), event.authLabelSnapshot.ifBlank { event.accountSnapshot.ifBlank { "—" } }) }
        item { DetailRow(stringResource(R.string.detail_tokens), event.totalTokens.compactNumber()) }
        item { DetailRow(stringResource(R.string.detail_latency), event.latencyMs?.let { "$it ms" } ?: "—") }
        if (event.failed) {
            item { DetailRow(stringResource(R.string.detail_error), SensitiveText.redact(event.failSummary).ifBlank { stringResource(R.string.request_failed) }) }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyLarge)
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}

private fun TrafficWindow.labelResource(): Int = when (this) {
    TrafficWindow.Hour -> R.string.last_hour
    TrafficWindow.Day -> R.string.last_24_hours
    TrafficWindow.Week -> R.string.last_7_days
}

private val SUCCESS_COLOR = Color(0xFF2E7D5B)
