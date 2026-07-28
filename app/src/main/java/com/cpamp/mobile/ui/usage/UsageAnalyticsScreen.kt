package com.cpamp.mobile.ui.usage

import android.content.Intent
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
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Token
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.cpamp.mobile.ui.common.asCost
import com.cpamp.mobile.ui.common.asPercent
import com.cpamp.mobile.ui.common.compactNumber
import com.cpamp.mobile.ui.components.AppBackground
import com.cpamp.mobile.ui.components.AnalyticsTrendPoint
import com.cpamp.mobile.ui.components.DashboardTrafficChart
import com.cpamp.mobile.ui.components.LoadingIconButton
import com.cpamp.mobile.ui.components.MetricCard
import com.cpamp.mobile.ui.components.ModelProviderIcon
import com.cpamp.mobile.ui.components.PageHeader

@Composable
fun UsageAnalyticsScreen(
    contentPadding: PaddingValues,
    viewModel: UsageAnalyticsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
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
            if (state.shareError) {
                item { UsageNotice(stringResource(R.string.share_usage_failed)) }
            }
            state.effectiveMonthRange?.takeIf { it.actualDays < 30 }?.let { range ->
                item { UsageRangeNotice(stringResource(R.string.usage_partial_range, range.actualDays)) }
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
                                value = summary.totalTokens.compactNumber(),
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
                    DashboardTrafficChart(
                        title = stringResource(R.string.usage_trend),
                        points = response.timeline.map { point ->
                            AnalyticsTrendPoint(
                                timestampMs = point.bucketMs,
                                requests = point.calls,
                                tokens = point.totalTokens.takeIf { it > 0 } ?: point.tokens,
                                bucketEndMs = point.bucketEndMs,
                                success = point.success,
                                failure = point.failure,
                                cost = point.cost,
                            )
                        },
                        nowMs = response.generatedAtMs.takeIf { it > 0 } ?: System.currentTimeMillis(),
                        emptyText = stringResource(R.string.no_traffic),
                        compactToData = false,
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
                when (state.ranking) {
                    UsageRanking.Models -> items(response.modelStats.sortedByDescending(ModelStatDto::calls).take(10)) {
                        RankingRow(it.model, it.calls, it.totalTokens, it.cost, it.successRate, model = it.model)
                    }
                    UsageRanking.ApiKeys -> items(response.apiKeyStats.sortedByDescending(ApiKeyStatDto::calls).take(10)) {
                        RankingRow(it.displayName, it.calls, it.totalTokens, it.cost, it.successRate)
                    }
                    UsageRanking.Credentials -> items(response.credentialStats.sortedByDescending(CredentialStatDto::calls).take(10)) {
                        RankingRow(it.displayName, it.calls, it.totalTokens, it.cost, it.successRate)
                    }
                }
            }
        }
    }
}

@Composable
private fun RankingRow(
    name: String,
    calls: Long,
    tokens: Long,
    cost: Double,
    successRate: Double,
    model: String? = null,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            model?.let { ModelProviderIcon(it) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
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
}

@Composable
private fun UsageNotice(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Text(text, modifier = Modifier.fillMaxWidth().padding(14.dp), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun UsageRangeNotice(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Text(
            text,
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

private val ApiKeyStatDto.displayName: String
    get() = authLabelSnapshot.ifBlank { accountSnapshot.ifBlank { apiKeyHash.ifBlank { id } } }

private val CredentialStatDto.displayName: String
    get() = authLabelSnapshot.ifBlank { accountSnapshot.ifBlank { authFileSnapshot.ifBlank { authIndex.ifBlank { id } } } }

private val UsageWindow.labelResource: Int
    get() = when (this) {
        UsageWindow.Day -> R.string.today
        UsageWindow.Week -> R.string.last_7_days
        UsageWindow.Month -> R.string.last_30_days
    }

private val UsageRanking.labelResource: Int
    get() = when (this) {
        UsageRanking.Models -> R.string.usage_models
        UsageRanking.ApiKeys -> R.string.usage_api_keys
        UsageRanking.Credentials -> R.string.usage_credentials
    }
