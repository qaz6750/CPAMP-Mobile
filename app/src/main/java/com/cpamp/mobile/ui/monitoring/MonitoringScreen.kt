package com.cpamp.mobile.ui.monitoring

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Toll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import com.cpamp.mobile.ui.common.SensitiveText
import com.cpamp.mobile.ui.common.asLatency
import com.cpamp.mobile.ui.common.asPercent
import com.cpamp.mobile.ui.common.asTime
import com.cpamp.mobile.ui.common.compactNumber
import com.cpamp.mobile.ui.common.compactTokens
import com.cpamp.mobile.ui.common.safeServerName
import com.cpamp.mobile.ui.components.AppBackground
import com.cpamp.mobile.ui.components.LoadingIconButton
import com.cpamp.mobile.ui.components.PageHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitoringScreen(
    contentPadding: PaddingValues,
    hideAddresses: Boolean,
    onOpenCredentialQuotas: () -> Unit,
    viewModel: MonitoringViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedEvent by remember { mutableStateOf<RequestEventDto?>(null) }

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
                val fallback = stringResource(R.string.nav_traffic)
                PageHeader(
                    eyebrow = state.profile?.let { profile ->
                        safeServerName(profile.name, profile.baseUrl, hideAddresses, fallback)
                    } ?: fallback,
                    title = stringResource(R.string.traffic_title),
                    subtitle = stringResource(R.string.traffic_subtitle),
                    trailing = {
                        LoadingIconButton(
                            icon = Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.refresh),
                            loading = state.refreshing,
                            onClick = viewModel::refresh,
                        )
                    },
                )
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TrafficWindow.entries.forEach { window ->
                                FilterChip(
                                    selected = state.filter.window == window,
                                    onClick = { viewModel.setWindow(window) },
                                    label = { Text(stringResource(window.labelResource())) },
                                    modifier = Modifier.weight(1f),
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
                                colors = FilterChipDefaults.filterChipColors(
                                    labelColor = MaterialTheme.colorScheme.error,
                                    iconColor = MaterialTheme.colorScheme.error,
                                    selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onErrorContainer,
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = state.filter.failedOnly,
                                    borderColor = MaterialTheme.colorScheme.error,
                                    selectedBorderColor = MaterialTheme.colorScheme.error,
                                ),
                            )
                            Text(
                                stringResource(R.string.refresh_after_filter),
                                modifier = Modifier.align(Alignment.CenterVertically),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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
                            value = summary.p95LatencyMs?.asLatency() ?: "—",
                            icon = Icons.Outlined.Speed,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            item {
                CredentialQuotaEntry(
                    state = state,
                    onClick = onOpenCredentialQuotas,
                )
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
                            Icon(Icons.AutoMirrored.Outlined.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
private fun CredentialQuotaEntry(state: MonitoringUiState, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(Icons.Outlined.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(R.string.credential_quota), fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        state.credentialQuotasLoading -> stringResource(R.string.credential_quota_loading)
                        state.credentialQuotasError -> stringResource(R.string.credential_quota_unavailable)
                        state.credentialQuotas.isEmpty() -> stringResource(R.string.credential_quota_empty)
                        else -> stringResource(R.string.credential_quota_count, state.credentialQuotas.size)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (state.credentialQuotasLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Outlined.ChevronRight, contentDescription = null)
            }
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
        Column(Modifier.height(96.dp).padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
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
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.width(5.dp).fillMaxHeight().background(
                    color = if (event.failed) MaterialTheme.colorScheme.error else SUCCESS_COLOR,
                    shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp),
                ),
            )
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.event_tokens_value, event.totalTokens.compactTokens()), style = MaterialTheme.typography.labelSmall)
                    event.latencyMs?.let { Text(stringResource(R.string.event_latency_value, it.asLatency()), style = MaterialTheme.typography.labelSmall) }
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
        item { DetailRow(stringResource(R.string.detail_tokens), event.totalTokens.compactTokens()) }
        item { DetailRow(stringResource(R.string.detail_latency), event.latencyMs?.asLatency() ?: "—") }
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

@StringRes
private fun TrafficWindow.labelResource(): Int = when (this) {
    TrafficWindow.Hour -> R.string.last_hour
    TrafficWindow.Day -> R.string.last_24_hours
    TrafficWindow.Week -> R.string.last_7_days
}

private val SUCCESS_COLOR = Color(0xFF2E7D5B)
