package com.cpamp.mobile.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cpamp.mobile.data.remote.model.MonitoringTimelineDto
import com.cpamp.mobile.ui.common.asLatency
import com.cpamp.mobile.ui.common.compactNumber
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun RequestHealthChart(
    title: String,
    subtitle: String,
    points: List<MonitoringTimelineDto>,
    emptyText: String,
    successLabel: String,
    failureLabel: String,
    latencyLabel: String,
    modifier: Modifier = Modifier,
    titleAction: (@Composable () -> Unit)? = null,
) {
    val colors = analyticsDetailChartColors()
    val surfaceColor = MaterialTheme.colorScheme.surface
    AnalyticsChartContainer(title, subtitle, modifier, titleAction) {
        ChartLegendRow(
            listOf(
                colors.healthSuccess to successLabel,
                colors.healthFailure to failureLabel,
                colors.healthLatency to latencyLabel,
            ),
        )
        val hasData = points.any { it.calls > 0 || it.success > 0 || it.failure > 0 || it.averageLatencyMs != null }
        if (!hasData) {
            EmptyChart(emptyText)
            return@AnalyticsChartContainer
        }
        val maxLatency = points.maxOf { it.averageLatencyMs ?: 0.0 }.coerceAtLeast(1.0)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            PercentAxis()
            Canvas(Modifier.weight(1f).height(220.dp).padding(horizontal = 3.dp)) {
                drawChartGrid(colors.grid)
                val groupWidth = size.width / points.size.coerceAtLeast(1)
                val latencyBarWidth = (groupWidth * 0.48f).coerceAtMost(16.dp.toPx()).coerceAtLeast(1.dp.toPx())
                points.forEachIndexed { index, point ->
                    val latency = point.averageLatencyMs ?: 0.0
                    if (latency <= 0.0) return@forEachIndexed
                    val center = categoryPosition(index, points.size, size.width)
                    val top = size.height * (1f - (latency / maxLatency).toFloat())
                    drawRect(
                        color = colors.healthLatency.copy(alpha = 0.64f),
                        topLeft = Offset(center - latencyBarWidth / 2f, top),
                        size = androidx.compose.ui.geometry.Size(latencyBarWidth, size.height - top),
                    )
                }
                val successOffsets = points.mapIndexed { index, point ->
                    Offset(
                        categoryPosition(index, points.size, size.width),
                        size.height * (1f - point.successRate()),
                    )
                }
                val failureOffsets = points.mapIndexed { index, point ->
                    Offset(
                        categoryPosition(index, points.size, size.width),
                        size.height * (1f - point.failureRate()),
                    )
                }
                drawSmoothLine(successOffsets, colors.healthSuccess, surfaceColor, showPoints = points.size <= 36)
                drawSmoothLine(failureOffsets, colors.healthFailure, surfaceColor, showPoints = points.size <= 36)
            }
            LatencyAxis(maxLatency, colors.healthLatency)
        }
        TimeTicks(points.map(MonitoringTimelineDto::bucketMs))
    }
}

@Composable
fun TokenStructureChart(
    title: String,
    subtitle: String,
    points: List<MonitoringTimelineDto>,
    emptyText: String,
    inputLabel: String,
    outputLabel: String,
    cachedLabel: String,
    reasoningLabel: String,
    modifier: Modifier = Modifier,
    titleAction: (@Composable () -> Unit)? = null,
) {
    val colors = analyticsDetailChartColors()
    AnalyticsChartContainer(title, subtitle, modifier, titleAction) {
        ChartLegendRow(
            listOf(
                colors.tokenInput to inputLabel,
                colors.tokenOutput to outputLabel,
                colors.tokenCached to cachedLabel,
                colors.tokenReasoning to reasoningLabel,
            ),
        )
        val hasData = points.any {
            it.inputTokens > 0 || it.outputTokens > 0 || it.allCachedTokens > 0 || it.reasoningTokens > 0
        }
        if (!hasData) {
            EmptyChart(emptyText)
            return@AnalyticsChartContainer
        }
        val maximum = points.maxOf {
            it.inputTokens + it.outputTokens + it.allCachedTokens + it.reasoningTokens
        }.coerceAtLeast(1)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            NumberAxis(maximum)
            Canvas(Modifier.weight(1f).height(220.dp).padding(horizontal = 3.dp)) {
                drawChartGrid(colors.grid)
                val groupWidth = size.width / points.size.coerceAtLeast(1)
                val barWidth = (groupWidth * 0.64f).coerceAtMost(22.dp.toPx()).coerceAtLeast(2.dp.toPx())
                points.forEachIndexed { index, point ->
                    val center = categoryPosition(index, points.size, size.width)
                    val values = listOf(point.inputTokens, point.outputTokens, point.allCachedTokens, point.reasoningTokens)
                    val segmentColors = listOf(colors.tokenInput, colors.tokenOutput, colors.tokenCached, colors.tokenReasoning)
                    var bottom = size.height
                    values.forEachIndexed { valueIndex, value ->
                        if (value > 0) {
                            val segmentHeight = size.height * value / maximum.toFloat()
                            val top = bottom - segmentHeight
                            drawRect(
                                color = segmentColors[valueIndex],
                                topLeft = Offset(center - barWidth / 2f, top),
                                size = androidx.compose.ui.geometry.Size(barWidth, segmentHeight),
                            )
                            bottom = top
                        }
                    }
                }
            }
        }
        TimeTicks(points.map(MonitoringTimelineDto::bucketMs), endPadding = 4.dp)
    }
}

@Composable
private fun AnalyticsChartContainer(
    title: String,
    subtitle: String,
    modifier: Modifier,
    titleAction: (@Composable () -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                titleAction?.invoke()
            }
            content()
        }
    }
}

@Composable
private fun ChartLegendRow(items: List<Pair<Color, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.chunked(if (items.size > 3) 2 else 3).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                rowItems.forEach { (color, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(7.dp).background(color, CircleShape))
                        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyChart(text: String) {
    Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PercentAxis() {
    Column(
        modifier = Modifier.width(38.dp).height(220.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.End,
    ) {
        listOf("100%", "50%", "0%").forEach {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LatencyAxis(maximum: Double, color: Color) {
    Column(
        modifier = Modifier.width(48.dp).height(220.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        listOf(maximum, maximum / 2, 0.0).forEach {
            Text(it.asLatency(), style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

@Composable
private fun NumberAxis(maximum: Long) {
    Column(
        modifier = Modifier.width(48.dp).height(220.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.End,
    ) {
        listOf(maximum, maximum / 2, 0).forEach {
            Text(it.compactNumber(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TimeTicks(timestamps: List<Long>, endPadding: androidx.compose.ui.unit.Dp = 48.dp) {
    val indices = chartTickIndices(timestamps.size)
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 42.dp, end = endPadding),
    ) {
        indices.forEachIndexed { tickIndex, index ->
            Text(
                timestamps[index].chartDate(timestamps),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = when (tickIndex) {
                    0 -> TextAlign.Start
                    indices.lastIndex -> TextAlign.End
                    else -> TextAlign.Center
                },
                maxLines = 2,
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawChartGrid(color: Color) {
    repeat(3) { row ->
        val y = size.height * row / 2f
        drawLine(
            color,
            Offset(0f, y),
            Offset(size.width, y),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSmoothLine(
    points: List<Offset>,
    color: Color,
    surfaceColor: Color,
    showPoints: Boolean,
) {
    if (points.isEmpty()) return
    val path = Path().apply {
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
    drawPath(path, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    if (showPoints) {
        points.forEach { point ->
            drawCircle(surfaceColor, 4.dp.toPx(), point)
            drawCircle(color, 2.5.dp.toPx(), point)
        }
    }
}

private fun categoryPosition(index: Int, count: Int, width: Float): Float =
    if (count <= 1) width / 2f else width * (index + 0.5f) / count

private fun Long.chartDate(timestamps: List<Long>): String {
    val span = (timestamps.lastOrNull() ?: this) - (timestamps.firstOrNull() ?: this)
    val pattern = when {
        span <= 24 * 60 * 60 * 1000L -> "HH:mm"
        span <= 7 * 24 * 60 * 60 * 1000L -> "MM/dd\nHH:mm"
        else -> "MM/dd"
    }
    return Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(pattern))
}

private fun chartTickIndices(count: Int): List<Int> {
    if (count <= 0) return emptyList()
    if (count <= 5) return (0 until count).toList()
    return (0..4).map { step -> (count - 1) * step / 4 }.distinct()
}

private fun MonitoringTimelineDto.successRate(): Float {
    successRate?.takeIf(Double::isFinite)?.let { return it.toFloat().coerceIn(0f, 1f) }
    val total = calls.takeIf { it > 0 } ?: (success + failure).coerceAtLeast(1)
    return (success.toFloat() / total).coerceIn(0f, 1f)
}

private fun MonitoringTimelineDto.failureRate(): Float {
    failureRate?.takeIf(Double::isFinite)?.let { return it.toFloat().coerceIn(0f, 1f) }
    val total = calls.takeIf { it > 0 } ?: (success + failure).coerceAtLeast(1)
    return (failure.toFloat() / total).coerceIn(0f, 1f)
}

private data class AnalyticsDetailChartColors(
    val healthSuccess: Color,
    val healthFailure: Color,
    val healthLatency: Color,
    val tokenInput: Color,
    val tokenOutput: Color,
    val tokenCached: Color,
    val tokenReasoning: Color,
    val grid: Color,
)

@Composable
private fun analyticsDetailChartColors(): AnalyticsDetailChartColors {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return if (dark) {
        AnalyticsDetailChartColors(
            healthSuccess = Color(0xFF95D475),
            healthFailure = Color(0xFFFAB6B6),
            healthLatency = Color(0xFF7DD3FC),
            tokenInput = Color(0xFF60A5FA),
            tokenOutput = Color(0xFF95D475),
            tokenCached = Color(0xFF22D3EE),
            tokenReasoning = Color(0xFFFBBF24),
            grid = Color.White.copy(alpha = 0.10f),
        )
    } else {
        AnalyticsDetailChartColors(
            healthSuccess = Color(0xFF67C23A),
            healthFailure = Color(0xFFF56C6C),
            healthLatency = Color(0xFF0EA5E9),
            tokenInput = Color(0xFF60A5FA),
            tokenOutput = Color(0xFF22C55E),
            tokenCached = Color(0xFF06B6D4),
            tokenReasoning = Color(0xFFF59E0B),
            grid = Color(0xFFD3E1EF),
        )
    }
}
