package com.cpamp.mobile.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cpamp.mobile.R
import com.cpamp.mobile.data.remote.model.TopModelDto
import com.cpamp.mobile.ui.common.asCost
import com.cpamp.mobile.ui.common.asPercent
import com.cpamp.mobile.ui.common.asTime
import com.cpamp.mobile.ui.common.compactNumber
import com.cpamp.mobile.ui.common.safeServerName
import com.cpamp.mobile.ui.components.AppBackground
import com.cpamp.mobile.ui.components.AnalyticsTrendPoint
import com.cpamp.mobile.ui.components.ConnectionPill
import com.cpamp.mobile.ui.components.DashboardTrafficChart
import com.cpamp.mobile.ui.components.LoadingIconButton
import com.cpamp.mobile.ui.components.MetricCard
import com.cpamp.mobile.ui.components.ModelProviderIcon
import com.cpamp.mobile.ui.components.PageHeader

@Composable
fun DashboardScreen(
    contentPadding: PaddingValues,
    hideAddresses: Boolean,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    AppBackground {
        if (state.loading && state.summary == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@AppBackground
        }

        val summary = state.summary
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = contentPadding.calculateTopPadding() + 24.dp,
                bottom = contentPadding.calculateBottomPadding() + 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                val fallback = stringResource(R.string.nav_overview)
                PageHeader(
                    eyebrow = state.profile?.let { profile ->
                        safeServerName(profile.name, profile.baseUrl, hideAddresses, fallback)
                    } ?: fallback,
                    title = stringResource(R.string.overview_title),
                    subtitle = stringResource(R.string.overview_subtitle),
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            state.profile?.let { profile ->
                                ConnectionPill(
                                    label = stringResource(
                                        if (profile.usesCleartext) R.string.http_connection else R.string.https_connection,
                                    ),
                                    secure = !profile.usesCleartext,
                                )
                            }
                            LoadingIconButton(
                                icon = Icons.Outlined.Refresh,
                                contentDescription = stringResource(R.string.refresh),
                                loading = state.refreshing,
                                onClick = viewModel::refresh,
                            )
                        }
                    },
                )
            }
            if (state.fromCache || state.error != null) {
                item { DashboardNotice(state) }
            }
            summary?.let { data ->
                item { DashboardMetrics(data) }
                item {
                    val timeline = state.analyticsTimeline
                    DashboardTrafficChart(
                        title = stringResource(R.string.preview_trend),
                        points = if (timeline.isNotEmpty()) {
                            timeline.map { point ->
                                AnalyticsTrendPoint(
                                    timestampMs = point.bucketMs,
                                    requests = point.calls,
                                    tokens = point.totalTokens.takeIf { it > 0 } ?: point.tokens,
                                    bucketEndMs = point.bucketEndMs,
                                    success = point.success,
                                    failure = point.failure,
                                    cost = point.cost,
                                )
                            }
                        } else {
                            data.trafficTimeline.map { point ->
                                AnalyticsTrendPoint(
                                    timestampMs = point.bucketMs,
                                    requests = point.calls,
                                    tokens = point.tokens,
                                    success = point.success,
                                    failure = point.failure,
                                )
                            }
                        },
                        nowMs = data.generatedAtMs.takeIf { it > 0 } ?: System.currentTimeMillis(),
                        emptyText = stringResource(R.string.no_traffic),
                    )
                }
                if (data.topModelsToday.isNotEmpty()) {
                    item { Text(stringResource(R.string.top_models), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
                    items(data.topModelsToday, key = TopModelDto::model) { TopModelRow(it) }
                }
            }
        }
    }
}

@Composable
private fun DashboardMetrics(data: com.cpamp.mobile.data.remote.model.DashboardSummaryDto) {
    val cards: List<@Composable (Modifier) -> Unit> = listOf(
        { modifier ->
            MetricCard(
                label = stringResource(R.string.metric_requests),
                value = data.today.totalCalls.compactNumber(),
                supporting = stringResource(R.string.rpm_value, data.rolling30m.rpm),
                icon = Icons.Outlined.DataUsage,
                modifier = modifier,
                compact = true,
            )
        },
        { modifier ->
            MetricCard(
                label = stringResource(R.string.metric_success),
                value = data.today.successRate.asPercent(),
                supporting = stringResource(R.string.failed_value, data.today.failureCalls),
                icon = Icons.Outlined.CheckCircle,
                modifier = modifier,
                accent = MaterialTheme.colorScheme.tertiary,
                compact = true,
            )
        },
        { modifier ->
            MetricCard(
                label = stringResource(R.string.metric_tokens),
                value = data.today.totalTokens.compactNumber(),
                supporting = stringResource(R.string.tpm_value, data.rolling30m.tpm),
                icon = Icons.Outlined.Speed,
                modifier = modifier,
                compact = true,
            )
        },
        { modifier ->
            MetricCard(
                label = stringResource(R.string.metric_cost),
                value = data.today.totalCost.asCost(),
                supporting = data.today.averageLatencyMs?.let { stringResource(R.string.latency_value, it) }
                    ?: stringResource(R.string.no_latency),
                icon = Icons.Outlined.Payments,
                modifier = modifier,
                accent = MaterialTheme.colorScheme.secondary,
                compact = true,
            )
        },
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        cards.chunked(2).forEach { rowCards ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowCards.forEach { card -> card(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DashboardNotice(state: DashboardUiState) {
    val text = when {
        state.error == DashboardError.Unauthorized -> stringResource(R.string.dashboard_unauthorized)
        state.error == DashboardError.RateLimited -> stringResource(R.string.dashboard_rate_limited)
        state.error == DashboardError.Timeout -> stringResource(R.string.dashboard_timeout)
        state.error != null -> stringResource(R.string.dashboard_offline)
        state.fromCache -> stringResource(
            R.string.cached_data_notice,
            state.updatedAt?.asTime().orEmpty(),
        )
        else -> ""
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Text(text, modifier = Modifier.fillMaxWidth().padding(14.dp), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TopModelRow(model: TopModelDto) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(17.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ModelProviderIcon(model.model)
            Column(modifier = Modifier.weight(1f)) {
                Text(model.model, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(R.string.model_usage, model.calls, model.tokens.compactNumber()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(model.cost.asCost(), fontWeight = FontWeight.SemiBold)
                Text(model.successRate.asPercent(), style = MaterialTheme.typography.labelSmall)
            }
        }
        HorizontalDivider()
    }
}
