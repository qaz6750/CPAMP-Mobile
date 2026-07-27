package com.cpamp.mobile.ui.usage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Token
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cpamp.mobile.R
import com.cpamp.mobile.data.remote.model.ApiKeyStatDto
import com.cpamp.mobile.data.remote.model.CredentialStatDto
import com.cpamp.mobile.data.remote.model.ModelStatDto
import com.cpamp.mobile.data.remote.model.MonitoringTimelineDto
import com.cpamp.mobile.ui.common.asCost
import com.cpamp.mobile.ui.common.asPercent
import com.cpamp.mobile.ui.common.asTime
import com.cpamp.mobile.ui.common.compactNumber
import com.cpamp.mobile.ui.components.AppBackground
import com.cpamp.mobile.ui.components.MetricCard
import com.cpamp.mobile.ui.components.PageHeader

private enum class RankingTab { Models, ApiKeys, Credentials }

@Composable
fun UsageAnalyticsScreen(
    contentPadding: PaddingValues,
    viewModel: UsageAnalyticsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var rankingTab = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(RankingTab.Models) }

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
                    eyebrow = stringResource(R.string.nav_usage),
                    title = stringResource(R.string.usage_title),
                    subtitle = stringResource(R.string.usage_subtitle),
                    trailing = {
                        IconButton(onClick = viewModel::refresh, enabled = !state.loading) {
                            if (state.loading) {
                                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
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
                    UsageWindow.entries.forEach { window ->
                        FilterChip(
                            selected = state.window == window,
                            onClick = { viewModel.setWindow(window) },
                            label = { Text(stringResource(window.labelResource)) },
                        )
                    }
                }
            }
            if (state.error) {
                item { UsageNotice(stringResource(R.string.usage_request_failed)) }
            }
            val response = state.response
            if (response == null && !state.loading) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Text(stringResource(R.string.usage_manual_prompt), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = viewModel::refresh) {
                                Icon(Icons.Outlined.Refresh, contentDescription = null)
                                Text(stringResource(R.string.refresh), modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            } else if (response != null) {
                response.summary?.let { summary ->
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MetricCard(
                                label = stringResource(R.string.usage_requests),
                                value = summary.totalCalls.compactNumber(),
                                supporting = summary.successRate.asPercent(),
                                icon = Icons.Outlined.DataUsage,
                                modifier = Modifier.weight(1f),
                            )
                            MetricCard(
                                label = stringResource(R.string.usage_tokens),
                                value = summary.totalTokens.compactNumber(),
                                supporting = stringResource(R.string.usage_success_rate),
                                icon = Icons.Outlined.Token,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MetricCard(
                                label = stringResource(R.string.usage_cost),
                                value = summary.totalCost.asCost(),
                                supporting = stringResource(R.string.usage_estimated),
                                icon = Icons.Outlined.Payments,
                                modifier = Modifier.weight(1f),
                            )
                            MetricCard(
                                label = stringResource(R.string.usage_success),
                                value = summary.successCalls.compactNumber(),
                                supporting = stringResource(R.string.usage_failures_value, summary.failureCalls),
                                icon = Icons.Outlined.CheckCircle,
                                modifier = Modifier.weight(1f),
                                accent = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                }
                item { UsageTimeline(response.timeline) }
                item {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RankingTab.entries.forEach { tab ->
                            FilterChip(
                                selected = rankingTab.value == tab,
                                onClick = { rankingTab.value = tab },
                                label = { Text(stringResource(tab.labelResource)) },
                            )
                        }
                    }
                }
                when (rankingTab.value) {
                    RankingTab.Models -> items(response.modelStats.sortedByDescending(ModelStatDto::calls).take(10)) {
                        RankingRow(it.model, it.calls, it.totalTokens, it.cost, it.successRate)
                    }
                    RankingTab.ApiKeys -> items(response.apiKeyStats.sortedByDescending(ApiKeyStatDto::calls).take(10)) {
                        RankingRow(it.displayName, it.calls, it.totalTokens, it.cost, it.successRate)
                    }
                    RankingTab.Credentials -> items(response.credentialStats.sortedByDescending(CredentialStatDto::calls).take(10)) {
                        RankingRow(it.displayName, it.calls, it.totalTokens, it.cost, it.successRate)
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageTimeline(points: List<MonitoringTimelineDto>) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.usage_trend), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (points.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_traffic), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val lineColor = MaterialTheme.colorScheme.primary
                val gridColor = MaterialTheme.colorScheme.outlineVariant
                Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                    val maximum = points.maxOf(MonitoringTimelineDto::calls).coerceAtLeast(1).toFloat()
                    repeat(4) { row ->
                        val y = size.height * row / 3f
                        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    }
                    val path = Path()
                    points.forEachIndexed { index, point ->
                        val x = if (points.size == 1) 0f else size.width * index / (points.size - 1f)
                        val y = size.height - size.height * point.calls / maximum
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, lineColor, style = Stroke(width = 5f, cap = StrokeCap.Round))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf(points.first(), points[points.lastIndex / 2], points.last()).forEach {
                        Text(it.bucketMs.asTime(), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun RankingRow(name: String, calls: Long, tokens: Long, cost: Double, successRate: Double) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(name.ifBlank { stringResource(R.string.unknown_value) }, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.usage_calls_value, calls.compactNumber()), style = MaterialTheme.typography.bodySmall)
                Text(tokens.compactNumber(), style = MaterialTheme.typography.bodySmall)
                Text(cost.asCost(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                Text(successRate.asPercent(), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun UsageNotice(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Text(text, modifier = Modifier.fillMaxWidth().padding(14.dp), style = MaterialTheme.typography.bodySmall)
    }
}

private val ApiKeyStatDto.displayName: String
    get() = authLabelSnapshot.ifBlank { accountSnapshot.ifBlank { apiKeyHash.ifBlank { id } } }

private val CredentialStatDto.displayName: String
    get() = authLabelSnapshot.ifBlank { accountSnapshot.ifBlank { authFileSnapshot.ifBlank { authIndex.ifBlank { id } } } }

private val UsageWindow.labelResource: Int
    get() = when (this) {
        UsageWindow.Day -> R.string.last_24_hours
        UsageWindow.Week -> R.string.last_7_days
        UsageWindow.Month -> R.string.last_30_days
    }

private val RankingTab.labelResource: Int
    get() = when (this) {
        RankingTab.Models -> R.string.usage_models
        RankingTab.ApiKeys -> R.string.usage_api_keys
        RankingTab.Credentials -> R.string.usage_credentials
    }