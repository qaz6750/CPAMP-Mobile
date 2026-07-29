package com.cpamp.mobile.ui.usage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cpamp.mobile.R
import com.cpamp.mobile.data.remote.model.MonitoringTimelineDto
import com.cpamp.mobile.ui.components.AnalyticsTrendPoint
import com.cpamp.mobile.ui.components.DashboardTrafficChart
import com.cpamp.mobile.ui.components.RequestHealthChart
import com.cpamp.mobile.ui.components.TokenStructureChart

internal enum class ExpandedChart { UsageTrend, RequestHealth, TokenStructure }

@Composable
internal fun ExpandedUsageChartDialog(
    chart: ExpandedChart,
    timeline: List<MonitoringTimelineDto>,
    nowMs: Long,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .requiredWidth(maxHeight)
                    .requiredHeight(maxWidth)
                    .graphicsLayer(rotationZ = 90f)
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                val closeAction: @Composable () -> Unit = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.close_expanded_chart),
                        )
                    }
                }
                when (chart) {
                    ExpandedChart.UsageTrend -> DashboardTrafficChart(
                        title = stringResource(R.string.usage_trend),
                        points = timeline.toAnalyticsTrendPoints(),
                        nowMs = nowMs,
                        emptyText = stringResource(R.string.no_traffic),
                        modifier = Modifier.fillMaxWidth(),
                        compactToData = false,
                        chartHeight = 220.dp,
                        titleAction = closeAction,
                    )
                    ExpandedChart.RequestHealth -> RequestHealthChart(
                        title = stringResource(R.string.request_health_trend),
                        subtitle = stringResource(R.string.request_health_subtitle),
                        points = timeline,
                        emptyText = stringResource(R.string.no_range_traffic),
                        successLabel = stringResource(R.string.health_success_rate),
                        failureLabel = stringResource(R.string.health_failure_rate),
                        latencyLabel = stringResource(R.string.health_average_latency),
                        modifier = Modifier.fillMaxWidth(),
                        titleAction = closeAction,
                    )
                    ExpandedChart.TokenStructure -> TokenStructureChart(
                        title = stringResource(R.string.token_structure),
                        subtitle = stringResource(R.string.token_structure_subtitle),
                        points = timeline,
                        emptyText = stringResource(R.string.no_token_structure),
                        inputLabel = stringResource(R.string.token_input),
                        outputLabel = stringResource(R.string.token_output),
                        cachedLabel = stringResource(R.string.token_cached),
                        reasoningLabel = stringResource(R.string.token_reasoning),
                        modifier = Modifier.fillMaxWidth(),
                        titleAction = closeAction,
                    )
                }
            }
        }
    }
}

internal fun List<MonitoringTimelineDto>.toAnalyticsTrendPoints(): List<AnalyticsTrendPoint> = map { point ->
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
