package com.cpamp.mobile.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.outlined.Api
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cpamp.mobile.R
import com.cpamp.mobile.data.remote.model.TopModelDto
import com.cpamp.mobile.data.remote.model.TrafficPointDto
import com.cpamp.mobile.ui.common.asCost
import com.cpamp.mobile.ui.common.asPercent
import com.cpamp.mobile.ui.common.asTime
import com.cpamp.mobile.ui.common.compactNumber
import com.cpamp.mobile.ui.components.AppBackground
import com.cpamp.mobile.ui.components.ConnectionPill
import com.cpamp.mobile.ui.components.MetricCard
import com.cpamp.mobile.ui.components.PageHeader

@Composable
fun DashboardScreen(
    contentPadding: PaddingValues,
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
                PageHeader(
                    eyebrow = state.profile?.name ?: stringResource(R.string.app_name),
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
                            IconButton(onClick = viewModel::refresh, enabled = !state.refreshing) {
                                if (state.refreshing) {
                                    CircularProgressIndicator(modifier = Modifier.height(22.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.refresh))
                                }
                            }
                        }
                    },
                )
            }
            if (state.fromCache || state.error != null) {
                item { DashboardNotice(state) }
            }
            summary?.let { data ->
                item { DashboardMetrics(data) }
                item { TrafficCard(data.trafficTimeline) }
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
            )
        },
        { modifier ->
            MetricCard(
                label = stringResource(R.string.metric_tokens),
                value = data.today.totalTokens.compactNumber(),
                supporting = stringResource(R.string.tpm_value, data.rolling30m.tpm),
                icon = Icons.Outlined.Speed,
                modifier = modifier,
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
            )
        },
    )
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compactLayout = maxWidth < 520.dp
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (compactLayout) {
                cards.forEach { card -> card(Modifier.fillMaxWidth()) }
            } else {
                cards.chunked(2).forEach { rowCards ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowCards.forEach { card -> card(Modifier.weight(1f)) }
                    }
                }
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
private fun TrafficCard(points: List<TrafficPointDto>) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.preview_trend), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (points.isEmpty() || points.maxOfOrNull(TrafficPointDto::calls) == 0L) {
                Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_traffic), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val description = stringResource(R.string.traffic_chart_description, points.sumOf(TrafficPointDto::calls))
                val lineColor = MaterialTheme.colorScheme.primary
                val gridColor = MaterialTheme.colorScheme.outlineVariant
                Canvas(
                    modifier = Modifier.fillMaxWidth().height(160.dp).semantics { contentDescription = description },
                ) {
                    val max = points.maxOf(TrafficPointDto::calls).coerceAtLeast(1).toFloat()
                    repeat(4) { row ->
                        val y = size.height * row / 3f
                        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    }
                    val path = Path()
                    points.forEachIndexed { index, point ->
                        val x = if (points.size == 1) 0f else size.width * index / (points.size - 1f)
                        val y = size.height - (size.height * point.calls / max)
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, lineColor, style = Stroke(width = 5f, cap = StrokeCap.Round))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf(points.first(), points[points.lastIndex / 2], points.last()).forEach { point ->
                        Text(
                            point.bucketMs.asTime(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
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
            Icon(Icons.Outlined.Api, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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

