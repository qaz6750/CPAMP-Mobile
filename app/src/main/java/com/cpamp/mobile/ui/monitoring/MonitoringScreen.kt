package com.cpamp.mobile.ui.monitoring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Toll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cpamp.mobile.R
import com.cpamp.mobile.ui.common.asPercent
import com.cpamp.mobile.ui.common.asTime
import com.cpamp.mobile.ui.common.compactNumber
import com.cpamp.mobile.ui.common.safeServerName
import com.cpamp.mobile.ui.components.AppBackground
import com.cpamp.mobile.ui.components.AppCard
import com.cpamp.mobile.ui.components.ContentStateCard
import com.cpamp.mobile.ui.components.LoadingIconButton
import com.cpamp.mobile.ui.components.PageHeader

@Composable
fun MonitoringScreen(
    contentPadding: PaddingValues,
    hideAddresses: Boolean,
    viewModel: MonitoringViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

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
                val fallback = stringResource(R.string.nav_monitoring)
                PageHeader(
                    eyebrow = state.profile?.let { profile ->
                        safeServerName(profile.name, profile.baseUrl, hideAddresses, fallback)
                    } ?: fallback,
                    title = stringResource(R.string.monitoring_title),
                    subtitle = stringResource(R.string.monitoring_subtitle),
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
                MonitoringFilterCard(
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
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(
                            Triple(R.string.metric_requests, summary.totalCalls.compactNumber(), Icons.Outlined.Toll),
                            Triple(R.string.metric_success, summary.successRate.asPercent(), Icons.Outlined.CheckCircle),
                        ).chunked(2).forEach { metrics ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                metrics.forEach { (label, value, icon) ->
                                    CompactMetric(
                                        label = stringResource(label),
                                        value = value,
                                        icon = icon,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (!state.loading && state.response?.summary == null) {
                item { ContentStateCard(message = stringResource(R.string.monitoring_empty_summary)) }
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
    AppCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
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
    AppCard(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.heightIn(min = 96.dp).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}
