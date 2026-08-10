package com.cpamp.mobile.ui.usage

import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Token
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cpamp.mobile.R
import com.cpamp.mobile.ui.common.asCost
import com.cpamp.mobile.ui.common.asPercent
import com.cpamp.mobile.ui.common.compactNumber
import com.cpamp.mobile.ui.common.compactTokens
import com.cpamp.mobile.ui.components.AppBackground
import com.cpamp.mobile.ui.components.AppCard
import com.cpamp.mobile.ui.components.DashboardTrafficChart
import com.cpamp.mobile.ui.components.LoadingIconButton
import com.cpamp.mobile.ui.components.MetricCard
import com.cpamp.mobile.ui.components.PageHeader
import com.cpamp.mobile.ui.components.RequestHealthChart
import com.cpamp.mobile.ui.components.TokenStructureChart
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun UsageAnalyticsScreen(
    contentPadding: PaddingValues,
    viewModel: UsageAnalyticsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var expandedChart by rememberSaveable { mutableStateOf<ExpandedChart?>(null) }
    LaunchedEffect(state.shareUri) {
        state.shareUri?.let { uri ->
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_usage)))
            viewModel.consumeShare()
        }
    }
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
                        Row {
                            LoadingIconButton(
                                icon = Icons.Outlined.Share,
                                contentDescription = stringResource(R.string.share_usage),
                                loading = state.sharing,
                                enabled = !state.loading,
                                onClick = viewModel::share,
                            )
                            LoadingIconButton(
                                icon = Icons.Outlined.Refresh,
                                contentDescription = stringResource(R.string.refresh),
                                loading = state.loading,
                                enabled = !state.sharing,
                                onClick = viewModel::refresh,
                            )
                        }
                    },
                )
            }
            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    UsageWindow.entries
                        .filter { it != UsageWindow.SpecificMonth || state.availableMonths.isNotEmpty() }
                        .forEach { window ->
                        FilterChip(
                            selected = state.window == window,
                            onClick = { viewModel.setWindow(window) },
                            label = { Text(stringResource(window.labelResource)) },
                        )
                    }
                }
            }
            if (state.window == UsageWindow.SpecificMonth) {
                item {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.availableMonths.forEach { month ->
                            FilterChip(
                                selected = state.selectedMonth == month,
                                onClick = { viewModel.setMonth(month) },
                                label = { Text(month.monthLabel()) },
                            )
                        }
                    }
                }
            }
            if (state.error) {
                item { UsageNotice(stringResource(R.string.usage_request_failed)) }
            }
            if (state.shareError) {
                item { UsageNotice(stringResource(R.string.share_usage_failed)) }
            }
            state.partialMonthRange?.let { range ->
                item { UsageRangeNotice(stringResource(R.string.usage_partial_range, range.actualDays)) }
            }
            val response = state.response
            if (response == null && !state.loading) {
                item {
                    AppCard(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)) {
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
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MetricCard(
                                label = stringResource(R.string.usage_requests),
                                value = summary.totalCalls.compactNumber(),
                                supporting = summary.successRate.asPercent(),
                                icon = Icons.Outlined.DataUsage,
                                modifier = Modifier.weight(1f),
                                compact = true,
                            )
                            MetricCard(
                                label = stringResource(R.string.usage_tokens),
                                value = summary.totalTokens.compactTokens(),
                                supporting = stringResource(R.string.usage_success_rate),
                                icon = Icons.Outlined.Token,
                                modifier = Modifier.weight(1f),
                                compact = true,
                            )
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MetricCard(
                                label = stringResource(R.string.usage_cost),
                                value = summary.totalCost.asCost(),
                                supporting = stringResource(R.string.usage_estimated),
                                icon = Icons.Outlined.Payments,
                                modifier = Modifier.weight(1f),
                                compact = true,
                            )
                            MetricCard(
                                label = stringResource(R.string.usage_success),
                                value = summary.successCalls.compactNumber(),
                                supporting = stringResource(R.string.usage_failures_value, summary.failureCalls),
                                icon = Icons.Outlined.CheckCircle,
                                modifier = Modifier.weight(1f),
                                accent = MaterialTheme.colorScheme.tertiary,
                                compact = true,
                            )
                        }
                    }
                }
                item {
                    val trendPoints = response.timeline.toAnalyticsTrendPoints()
                    DashboardTrafficChart(
                        title = stringResource(R.string.usage_trend),
                        points = trendPoints,
                        nowMs = response.generatedAtMs.takeIf { it > 0 } ?: System.currentTimeMillis(),
                        emptyText = stringResource(R.string.no_traffic),
                        compactToData = false,
                        titleAction = {
                            IconButton(onClick = { expandedChart = ExpandedChart.UsageTrend }) {
                                Icon(Icons.Outlined.Fullscreen, contentDescription = stringResource(R.string.expand_chart))
                            }
                        },
                    )
                }
                item {
                    RequestHealthChart(
                        title = stringResource(R.string.request_health_trend),
                        subtitle = stringResource(R.string.request_health_subtitle),
                        points = response.timeline,
                        emptyText = stringResource(R.string.no_range_traffic),
                        successLabel = stringResource(R.string.health_success_rate),
                        failureLabel = stringResource(R.string.health_failure_rate),
                        latencyLabel = stringResource(R.string.health_average_latency),
                        titleAction = {
                            IconButton(onClick = { expandedChart = ExpandedChart.RequestHealth }) {
                                Icon(Icons.Outlined.Fullscreen, contentDescription = stringResource(R.string.expand_chart))
                            }
                        },
                    )
                }
                item {
                    TokenStructureChart(
                        title = stringResource(R.string.token_structure),
                        subtitle = stringResource(R.string.token_structure_subtitle),
                        points = response.timeline,
                        emptyText = stringResource(R.string.no_token_structure),
                        inputLabel = stringResource(R.string.token_input),
                        outputLabel = stringResource(R.string.token_output),
                        cachedLabel = stringResource(R.string.token_cached),
                        reasoningLabel = stringResource(R.string.token_reasoning),
                        titleAction = {
                            IconButton(onClick = { expandedChart = ExpandedChart.TokenStructure }) {
                                Icon(Icons.Outlined.Fullscreen, contentDescription = stringResource(R.string.expand_chart))
                            }
                        },
                    )
                }
                item {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        UsageRanking.entries.forEach { tab ->
                            FilterChip(
                                selected = state.ranking == tab,
                                onClick = { viewModel.setRanking(tab) },
                                label = { Text(stringResource(tab.labelResource)) },
                            )
                        }
                    }
                }
                usageRankingItems(state.ranking, response)
            }
        }
    }
    expandedChart?.let { chart ->
        ExpandedUsageChartDialog(
            chart = chart,
            timeline = state.response?.timeline.orEmpty(),
            nowMs = state.response?.generatedAtMs?.takeIf { it > 0 } ?: System.currentTimeMillis(),
            onDismiss = { expandedChart = null },
        )
    }
}

@Composable
private fun UsageNotice(text: String) {
    AppCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
        Text(text, modifier = Modifier.fillMaxWidth().padding(14.dp), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun UsageRangeNotice(text: String) {
    AppCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Text(
            text,
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@get:StringRes
private val UsageWindow.labelResource: Int
    get() = when (this) {
        UsageWindow.Day -> R.string.today
        UsageWindow.Week -> R.string.last_7_days
        UsageWindow.Month -> R.string.this_month
        UsageWindow.SpecificMonth -> R.string.specific_month
    }

private fun YearMonth.monthLabel(): String =
    format(DateTimeFormatter.ofPattern("yyyy-MM"))

@get:StringRes
private val UsageRanking.labelResource: Int
    get() = when (this) {
        UsageRanking.Models -> R.string.usage_models
        UsageRanking.ApiKeys -> R.string.usage_api_keys
        UsageRanking.Credentials -> R.string.usage_credentials
    }
