package com.cpamp.mobile.ui.monitoring

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Toll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cpamp.mobile.R
import com.cpamp.mobile.data.monitoring.CredentialAccountStatus
import com.cpamp.mobile.data.remote.model.RequestEventDto
import com.cpamp.mobile.ui.common.asLatency
import com.cpamp.mobile.ui.common.asPercent
import com.cpamp.mobile.ui.common.asTime
import com.cpamp.mobile.ui.common.compactNumber
import com.cpamp.mobile.ui.common.safeServerName
import com.cpamp.mobile.ui.components.AppBackground
import com.cpamp.mobile.ui.components.ContentStateCard
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
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
                TrafficFilterCard(
                    filter = state.filter,
                    onWindowSelected = viewModel::setWindow,
                    onFailedOnlyChanged = viewModel::setFailedOnly,
                )
            }
            if (state.fromCache || state.error != null) {
                item { MonitoringNotice(state) }
            }
            if (state.loading && state.response == null) {
                item {
                    ContentStateCard(message = stringResource(R.string.content_loading), loading = true)
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
            val events = state.visibleEvents
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
                    state.response?.events?.let {
                        Text(
                            stringResource(R.string.event_count, state.visibleEventCount),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (events.isEmpty() && !state.loading) {
                item {
                    ContentStateCard(
                        message = stringResource(R.string.no_matching_requests) + "\n" +
                            stringResource(
                                if (state.filter.failedOnly) R.string.failed_filter_empty_hint else R.string.adjust_filters,
                            ),
                    )
                }
            } else {
                items(events, key = RequestEventDto::stableId) { event ->
                    RequestEventCard(event = event, onClick = { selectedEvent = event })
                }
            }
        }
    }

    selectedEvent?.let { event ->
        ModalBottomSheet(
            onDismissRequest = { selectedEvent = null },
            containerColor = MaterialTheme.colorScheme.background,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            RequestEventDetails(event)
        }
    }
}

@Composable
private fun CredentialQuotaEntry(state: MonitoringUiState, onClick: () -> Unit) {
    val queryableCount = state.credentialQuotas.count { quota ->
        quota.accountStatus == CredentialAccountStatus.Active && quota.provider in setOf("codex", "xai")
    }
    Card(
        onClick = onClick,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                        else -> stringResource(
                            R.string.credential_quota_count,
                            state.credentialQuotas.size,
                            queryableCount,
                        )
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
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.height(96.dp).padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
