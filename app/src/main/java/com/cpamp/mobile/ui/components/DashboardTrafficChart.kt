package com.cpamp.mobile.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cpamp.mobile.R
import com.cpamp.mobile.ui.common.asCost
import com.cpamp.mobile.ui.common.compactNumber
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun DashboardTrafficChart(
    title: String,
    points: List<AnalyticsTrendPoint>,
    nowMs: Long,
    emptyText: String,
    modifier: Modifier = Modifier,
    compactToData: Boolean = true,
) {
    val visiblePoints = remember(points, nowMs, compactToData) {
        if (compactToData) dashboardVisibleTrafficPoints(points, nowMs)
        else points.filter { it.timestampMs <= nowMs }
    }
    val hasData = visiblePoints.any { it.requests > 0 || it.tokens > 0 || (it.cost ?: 0.0) > 0 }
    val chartDescription = stringResource(
        R.string.traffic_chart_description,
        visiblePoints.sumOf(AnalyticsTrendPoint::requests),
    )
    var selectedIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    LaunchedEffect(visiblePoints) {
        selectedIndex = selectedIndex?.takeIf(visiblePoints.indices::contains)
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ChartLegend(MaterialTheme.colorScheme.primary, stringResource(R.string.trend_requests))
                ChartLegend(MaterialTheme.colorScheme.tertiary, stringResource(R.string.trend_tokens))
                ChartLegend(CostLineColor, stringResource(R.string.trend_cost))
            }
            if (!hasData) {
                Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                    Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@Column
            }

            val requestColor = MaterialTheme.colorScheme.primary
            val tokenColor = MaterialTheme.colorScheme.tertiary
            val costColor = CostLineColor
            val gridColor = MaterialTheme.colorScheme.outlineVariant
            val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
            val surfaceColor = MaterialTheme.colorScheme.surface
            val maxTokens = visiblePoints.maxOf { it.tokens }.coerceAtLeast(1)
            val maxRequests = visiblePoints.maxOf { it.requests }.coerceAtLeast(1)
            val hasCost = visiblePoints.any { it.cost != null }
            val maxCost = visiblePoints.maxOf { it.cost ?: 0.0 }.coerceAtLeast(0.0001)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                ChartAxisLabels(maxRequests, Modifier.width(38.dp), Alignment.End, requestColor)
                Canvas(
                    modifier = Modifier.weight(1f).height(180.dp).padding(horizontal = 4.dp)
                        .pointerInput(visiblePoints) {
                            detectTapGestures { selectedIndex = trendPointIndex(it.x, size.width.toFloat(), visiblePoints.size) }
                        }
                        .pointerInput(visiblePoints) {
                            detectHorizontalDragGestures { change, _ ->
                                selectedIndex = trendPointIndex(change.position.x, size.width.toFloat(), visiblePoints.size)
                            }
                        }
                        .semantics { contentDescription = chartDescription },
                ) {
                    repeat(3) { row ->
                        val y = size.height * row / 2f
                        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                    }
                    val requestOffsets = visiblePoints.mapIndexed { index, point ->
                        Offset(chartX(index, visiblePoints.size, size.width), size.height * (1f - point.requests / maxRequests.toFloat()))
                    }
                    val tokenOffsets = visiblePoints.mapIndexed { index, point ->
                        Offset(chartX(index, visiblePoints.size, size.width), size.height * (1f - point.tokens / maxTokens.toFloat()))
                    }
                    val costOffsets = visiblePoints.mapIndexed { index, point ->
                        Offset(chartX(index, visiblePoints.size, size.width), size.height * (1f - (point.cost ?: 0.0) / maxCost).toFloat())
                    }
                    drawPath(
                        smoothChartPath(requestOffsets),
                        requestColor,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                    drawPath(
                        smoothChartPath(tokenOffsets),
                        tokenColor,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                    if (hasCost) {
                        drawPath(
                            smoothChartPath(costOffsets),
                            costColor,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                        )
                    }
                    selectedIndex?.let { index ->
                        val x = requestOffsets[index].x
                        drawLine(labelColor, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                        listOf(
                            requestOffsets[index] to requestColor,
                            tokenOffsets[index] to tokenColor,
                        ).forEach { (offset, color) ->
                            drawCircle(surfaceColor, 6.dp.toPx(), offset)
                            drawCircle(color, 4.dp.toPx(), offset)
                        }
                        if (hasCost) {
                            drawCircle(surfaceColor, 6.dp.toPx(), costOffsets[index])
                            drawCircle(costColor, 4.dp.toPx(), costOffsets[index])
                        }
                    }
                }
                ChartAxisLabels(maxTokens, Modifier.width(40.dp), Alignment.Start, tokenColor)
                CostAxisLabels(if (hasCost) maxCost else 0.0, Modifier.width(48.dp), costColor)
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 42.dp, end = 88.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                dashboardChartTicks(visiblePoints).forEach { point ->
                    Text(point.timestampMs.asChartLabel(visiblePoints), style = MaterialTheme.typography.labelSmall, color = labelColor)
                }
            }
            selectedIndex?.let { index -> DashboardPointDetails(visiblePoints[index]) }
        }
    }
}

@Composable
private fun ChartLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ChartAxisLabels(
    maximum: Long,
    modifier: Modifier,
    alignment: Alignment.Horizontal = Alignment.End,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Column(
        modifier = modifier.height(180.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = alignment,
    ) {
        Text(maximum.compactNumber(), style = MaterialTheme.typography.labelSmall, color = color)
        Text((maximum / 2).compactNumber(), style = MaterialTheme.typography.labelSmall, color = color)
        Text("0", style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun CostAxisLabels(maximum: Double, modifier: Modifier, color: Color) {
    Column(
        modifier = modifier.height(180.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(maximum.asCost(), style = MaterialTheme.typography.labelSmall, color = color)
        Text((maximum / 2).asCost(), style = MaterialTheme.typography.labelSmall, color = color)
        Text("$0", style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun DashboardPointDetails(point: AnalyticsTrendPoint) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            stringResource(
                R.string.trend_time_range,
                point.timestampMs.asDashboardDateTime(),
                (point.timestampMs + DASHBOARD_BUCKET_MS).asDashboardDateTime(),
            ),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.trend_requests_value, point.requests.compactNumber()), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.trend_tokens_value, point.tokens.compactNumber()), style = MaterialTheme.typography.bodySmall)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.trend_success_failure, point.success, point.failure), style = MaterialTheme.typography.bodySmall)
            point.cost?.let { Text(stringResource(R.string.trend_cost_value, it.asCost()), style = MaterialTheme.typography.bodySmall) }
        }
    }
}

internal fun dashboardVisibleTrafficPoints(
    points: List<AnalyticsTrendPoint>,
    nowMs: Long,
): List<AnalyticsTrendPoint> {
    val elapsed = points.filter { it.timestampMs <= nowMs }
    val firstData = elapsed.indexOfFirst { it.requests > 0 || it.tokens > 0 }
    return if (firstData >= 0) elapsed.drop(firstData) else elapsed
}

internal fun isCurrentTrafficBucket(
    point: AnalyticsTrendPoint,
    nowMs: Long,
): Boolean = nowMs >= point.timestampMs && nowMs < point.timestampMs + DASHBOARD_BUCKET_MS

private fun smoothChartPath(points: List<Offset>): Path = Path().apply {
    if (points.isEmpty()) return@apply
    moveTo(points.first().x, points.first().y)
    points.zipWithNext().forEach { (previous, current) ->
        val midpoint = (previous.x + current.x) / 2f
        cubicTo(midpoint, previous.y, midpoint, current.y, current.x, current.y)
    }
}

private fun chartX(index: Int, pointCount: Int, width: Float): Float =
    if (pointCount <= 1) width / 2f else width * index / (pointCount - 1f)

private fun dashboardChartTicks(points: List<AnalyticsTrendPoint>): List<AnalyticsTrendPoint> {
    if (points.size <= 2) return points
    return listOf(0, points.lastIndex / 2, points.lastIndex).distinct().map(points::get)
}

private fun Long.asChartLabel(points: List<AnalyticsTrendPoint>): String {
    val span = (points.lastOrNull()?.timestampMs ?: this) - (points.firstOrNull()?.timestampMs ?: this)
    val pattern = when {
        span <= 24 * 60 * 60 * 1000L -> "HH:mm"
        span <= 7 * 24 * 60 * 60 * 1000L -> "MM/dd HH:mm"
        else -> "MM/dd"
    }
    return Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(pattern))
}

private fun Long.asDashboardDateTime(): String = Instant.ofEpochMilli(this)
    .atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))

private const val DASHBOARD_BUCKET_MS = 60 * 60 * 1000L
private val CostLineColor = Color(0xFFF59E0B)
