package com.cpamp.mobile.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cpamp.mobile.R
import com.cpamp.mobile.common.MILLIS_PER_DAY
import com.cpamp.mobile.common.MILLIS_PER_HOUR
import com.cpamp.mobile.common.MILLIS_PER_WEEK
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
    chartHeight: Dp = 220.dp,
    titleAction: (@Composable () -> Unit)? = null,
) {
    val chartColors = usageTrendChartColors()
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
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                titleAction?.invoke()
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ChartLegend(chartColors.requests, stringResource(R.string.trend_requests))
                ChartLegend(chartColors.tokens, stringResource(R.string.trend_tokens))
                ChartLegend(chartColors.cost, stringResource(R.string.trend_cost))
            }
            if (!hasData) {
                Box(Modifier.fillMaxWidth().height(chartHeight), contentAlignment = Alignment.Center) {
                    Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@Column
            }

            val requestColor = chartColors.requests
            val tokenColor = chartColors.tokens
            val costColor = chartColors.cost
            val gridColor = chartColors.grid
            val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
            val surfaceColor = MaterialTheme.colorScheme.surface
            val requestRange = chartRange(visiblePoints.map { it.requests.toDouble() })
            val tokenRange = chartRange(visiblePoints.map { it.tokens.toDouble() })
            val hasCost = visiblePoints.any { it.cost != null }
            val costRange = chartRange(visiblePoints.map { it.cost ?: 0.0 }, minimumSpan = 0.0001)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                ChartAxisLabels(
                    range = requestRange,
                    height = chartHeight,
                    modifier = Modifier.width(34.dp),
                    alignment = Alignment.End,
                    color = requestColor,
                    formatter = { it.toLong().compactNumber() },
                )
                Canvas(
                    modifier = Modifier.weight(1f).height(chartHeight).padding(horizontal = 2.dp)
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
                        drawLine(
                            gridColor,
                            Offset(0f, y),
                            Offset(size.width, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
                        )
                    }
                    val requestOffsets = visiblePoints.mapIndexed { index, point ->
                        Offset(chartX(index, visiblePoints.size, size.width), requestRange.y(point.requests.toDouble(), size.height))
                    }
                    val tokenOffsets = visiblePoints.mapIndexed { index, point ->
                        Offset(chartX(index, visiblePoints.size, size.width), tokenRange.y(point.tokens.toDouble(), size.height))
                    }
                    val costOffsets = visiblePoints.mapIndexed { index, point ->
                        Offset(chartX(index, visiblePoints.size, size.width), costRange.y(point.cost ?: 0.0, size.height))
                    }
                    listOf(requestOffsets to requestColor, tokenOffsets to tokenColor).forEach { (offsets, color) ->
                        val areaPath = smoothChartPath(offsets).apply {
                            lineTo(offsets.last().x, size.height)
                            lineTo(offsets.first().x, size.height)
                            close()
                        }
                        drawPath(
                            path = areaPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(color.copy(alpha = 0.18f), Color.Transparent),
                                endY = size.height,
                            ),
                        )
                    }
                    drawPath(
                        smoothChartPath(requestOffsets),
                        requestColor,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                    drawPath(
                        smoothChartPath(tokenOffsets),
                        tokenColor,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                    if (hasCost) {
                        drawPath(
                            smoothChartPath(costOffsets),
                            costColor,
                            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
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
                CombinedAxisLabels(
                    tokenRange = tokenRange,
                    costRange = costRange,
                    showCost = hasCost,
                    height = chartHeight,
                    modifier = Modifier.width(48.dp),
                    tokenColor = tokenColor,
                    costColor = costColor,
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 32.dp, end = 50.dp),
            ) {
                val ticks = dashboardChartTicks(visiblePoints)
                ticks.forEachIndexed { index, point ->
                    Text(
                        text = point.timestampMs.asChartLabel(visiblePoints),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = labelColor,
                        textAlign = when (index) {
                            0 -> TextAlign.Start
                            ticks.lastIndex -> TextAlign.End
                            else -> TextAlign.Center
                        },
                        maxLines = 2,
                    )
                }
            }
            selectedIndex?.let { index ->
                DashboardPointDetails(
                    point = visiblePoints[index],
                    endMs = trendBucketEnd(visiblePoints, index),
                )
            }
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
    range: ChartRange,
    height: Dp,
    modifier: Modifier,
    alignment: Alignment.Horizontal = Alignment.End,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    formatter: (Double) -> String,
) {
    Column(
        modifier = modifier.height(height),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = alignment,
    ) {
        range.levels().forEach { value ->
            Text(formatter(value), style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

@Composable
private fun CombinedAxisLabels(
    tokenRange: ChartRange,
    costRange: ChartRange,
    showCost: Boolean,
    height: Dp,
    modifier: Modifier,
    tokenColor: Color,
    costColor: Color,
) {
    val tokenLevels = tokenRange.levels()
    val costLevels = costRange.levels()
    Column(
        modifier = modifier.height(height),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.Start,
    ) {
        tokenLevels.indices.forEach { index ->
            Column {
                Text(tokenLevels[index].toLong().compactNumber(), style = MaterialTheme.typography.labelSmall, color = tokenColor)
                if (showCost) {
                    Text(costLevels[index].asCost(), style = MaterialTheme.typography.labelSmall, color = costColor)
                }
            }
        }
    }
}

@Composable
private fun DashboardPointDetails(point: AnalyticsTrendPoint, endMs: Long) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            stringResource(
                R.string.trend_time_range,
                point.timestampMs.asDashboardDateTime(),
                endMs.asDashboardDateTime(),
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
): List<AnalyticsTrendPoint> = points.filter { it.timestampMs <= nowMs }

internal fun isCurrentTrafficBucket(
    point: AnalyticsTrendPoint,
    nowMs: Long,
): Boolean = nowMs >= point.timestampMs && nowMs < point.timestampMs + MILLIS_PER_HOUR

private fun smoothChartPath(points: List<Offset>): Path = Path().apply {
    if (points.isEmpty()) return@apply
    moveTo(points.first().x, points.first().y)
    points.zipWithNext().forEach { (previous, current) ->
        val controlOffset = (current.x - previous.x) * 0.25f
        cubicTo(
            previous.x + controlOffset,
            previous.y,
            current.x - controlOffset,
            current.y,
            current.x,
            current.y,
        )
    }
}

private data class ChartRange(val minimum: Double, val maximum: Double) {
    fun y(value: Double, height: Float): Float =
        (height * (1.0 - (value - minimum) / (maximum - minimum))).toFloat().coerceIn(0f, height)

    fun levels(): List<Double> = listOf(maximum, (minimum + maximum) / 2.0, minimum)
}

private fun chartRange(values: List<Double>, minimumSpan: Double = 1.0): ChartRange {
    val finite = values.filter(Double::isFinite)
    if (finite.isEmpty()) return ChartRange(0.0, minimumSpan)
    val minimum = finite.min()
    val maximum = finite.max()
    if (maximum > minimum) {
        val padding = (maximum - minimum) * 0.06
        return ChartRange((minimum - padding).coerceAtLeast(0.0), maximum + padding)
    }
    val padding = maxOf(kotlin.math.abs(maximum) * 0.08, minimumSpan / 2.0)
    return ChartRange((minimum - padding).coerceAtLeast(0.0), maximum + padding)
}

private data class UsageTrendChartColors(
    val requests: Color,
    val tokens: Color,
    val cost: Color,
    val grid: Color,
)

@Composable
private fun usageTrendChartColors(): UsageTrendChartColors {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return UsageTrendChartColors(
        requests = MaterialTheme.colorScheme.primary,
        tokens = if (dark) Color(0xFF2DD4BF) else Color(0xFF14B8A6),
        cost = if (dark) Color(0xFFFBBF24) else Color(0xFFF59E0B),
        grid = MaterialTheme.colorScheme.outlineVariant,
    )
}

private fun chartX(index: Int, pointCount: Int, width: Float): Float =
    if (pointCount <= 1) width / 2f else width * index / (pointCount - 1f)

private fun dashboardChartTicks(points: List<AnalyticsTrendPoint>): List<AnalyticsTrendPoint> {
    if (points.size <= 5) return points
    return (0..4).map { step -> points[points.lastIndex * step / 4] }.distinct()
}

private fun Long.asChartLabel(points: List<AnalyticsTrendPoint>): String {
    val span = (points.lastOrNull()?.timestampMs ?: this) - (points.firstOrNull()?.timestampMs ?: this)
    val pattern = when {
        span <= MILLIS_PER_DAY -> "HH:mm"
        span <= MILLIS_PER_WEEK -> "MM/dd\nHH:mm"
        else -> "MM/dd"
    }
    return Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(pattern))
}

private fun Long.asDashboardDateTime(): String = Instant.ofEpochMilli(this)
    .atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
