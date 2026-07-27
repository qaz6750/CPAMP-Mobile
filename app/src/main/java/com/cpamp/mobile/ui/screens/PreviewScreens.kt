package com.cpamp.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cpamp.mobile.R
import com.cpamp.mobile.ui.components.AppBackground
import com.cpamp.mobile.ui.components.ConnectionPill
import com.cpamp.mobile.ui.components.MetricCard
import com.cpamp.mobile.ui.components.PageHeader

@Composable
fun OverviewPreviewScreen(contentPadding: PaddingValues) {
    AppBackground {
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
                    eyebrow = stringResource(R.string.app_name),
                    title = stringResource(R.string.overview_title),
                    subtitle = stringResource(R.string.overview_subtitle),
                    trailing = { ConnectionPill(stringResource(R.string.demo_offline), secure = true) },
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MetricCard(
                        label = stringResource(R.string.metric_requests),
                        value = "—",
                        supporting = stringResource(R.string.awaiting_server),
                        icon = Icons.Outlined.DataUsage,
                        modifier = Modifier.weight(1f),
                    )
                    MetricCard(
                        label = stringResource(R.string.metric_success),
                        value = "—",
                        supporting = stringResource(R.string.awaiting_server),
                        icon = Icons.Outlined.CheckCircle,
                        modifier = Modifier.weight(1f),
                        accent = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MetricCard(
                        label = stringResource(R.string.metric_tokens),
                        value = "—",
                        supporting = stringResource(R.string.awaiting_server),
                        icon = Icons.Outlined.Speed,
                        modifier = Modifier.weight(1f),
                    )
                    MetricCard(
                        label = stringResource(R.string.metric_cost),
                        value = "—",
                        supporting = stringResource(R.string.awaiting_server),
                        icon = Icons.Outlined.Payments,
                        modifier = Modifier.weight(1f),
                        accent = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            item { PreviewPanel(stringResource(R.string.preview_trend)) }
        }
    }
}

@Composable
fun TrafficPreviewScreen(contentPadding: PaddingValues) = PreviewListScreen(
    contentPadding = contentPadding,
    eyebrow = stringResource(R.string.nav_traffic),
    title = stringResource(R.string.traffic_title),
    subtitle = stringResource(R.string.traffic_subtitle),
    rows = listOf(stringResource(R.string.preview_live), stringResource(R.string.preview_failures)),
)

@Composable
fun SystemPreviewScreen(contentPadding: PaddingValues) = PreviewListScreen(
    contentPadding = contentPadding,
    eyebrow = stringResource(R.string.nav_operations),
    title = stringResource(R.string.system_title),
    subtitle = stringResource(R.string.system_subtitle),
    rows = listOf(
        stringResource(R.string.system_status),
        stringResource(R.string.system_logs),
        stringResource(R.string.system_servers),
    ),
)

@Composable
private fun PreviewListScreen(
    contentPadding: PaddingValues,
    eyebrow: String,
    title: String,
    subtitle: String,
    rows: List<String>,
) {
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
            item { PageHeader(eyebrow, title, subtitle) }
            items(rows) { row -> PreviewPanel(row) }
        }
    }
}

@Composable
private fun PreviewPanel(title: String) {
    Card(
        modifier = Modifier.fillMaxWidth().widthIn(max = 960.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.DataUsage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.connect_to_view),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
