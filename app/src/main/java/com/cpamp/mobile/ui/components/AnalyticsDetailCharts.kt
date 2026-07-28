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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
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
) {
    AnalyticsChartContainer(title, subtitle, modifier) {
        ChartLegendRow(
            listOf(
                HealthSuccessColor to successLabel,
                HealthFailureColor to failureLabel,
                HealthLatencyColor to latencyLabel,
            ),
        )
        val visible = points.filter { it.calls > 0 || it.success > 0 || it.failure > 0 || it.averageLatencyMs != null }
        if (visible.isEmpty()) {
            EmptyChart(emptyText)
            return@AnalyticsChartContainer
        }
        val maxLatency = visible.maxOf { it.averageLatencyMs ?: 0.0 }.coerceAtLeast(1.0)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            PercentAxis()
            Canvas(Modifier.weight(1f).height(220.dp).padding(horizontal = 3.dp)) {
                drawChartGrid(HealthGridColor)
                val successOffsets = visible.mapIndexed { index, point ->
                    val total = (point.success + point.failure).coerceAtLeast(1)
                    Offset(chartPosition(index, visible.size, size.width), size.height * (1f - point.success / total.toFloat()))
                }
                val failureOffsets = visible.mapIndexed { index, point ->
                    val total = (point.success + point.failure).coerceAtLeast(1)
                    Offset(chartPosition(index, visible.size, size.width), size.height * (1f - point.failure / total.toFloat()))
                }
                val latencyOffsets = visible.mapIndexed { index, point ->
                    Offset(
                        chartPosition(index, visible.size, size.width),
                        size.height * (1f - ((point.averageLatencyMs ?: 0.0) / maxLatency).toFloat()),
                    )
                }
                drawSmoothLine(successOffsets, HealthSuccessColor)
                drawSmoothLine(failureOffsets, HealthFailureColor)
                drawSmoothLine(latencyOffsets, HealthLatencyColor)
            }
            LatencyAxis(maxLatency)
        }
        TimeTicks(visible.map(MonitoringTimelineDto::bucketMs))
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
) {
    AnalyticsChartContainer(title, subtitle, modifier) {
        ChartLegendRow(
            listOf(
                InputTokenColor to inputLabel,
                OutputTokenColor to outputLabel,
                CachedTokenColor to cachedLabel,
                ReasoningTokenColor to reasoningLabel,
            ),
        )
        val visible = points.filter {
            it.inputTokens > 0 || it.outputTokens > 0 || it.cachedTokens > 0 || it.reasoningTokens > 0
        }
        if (visible.isEmpty()) {
            EmptyChart(emptyText)
            return@AnalyticsChartContainer
        }
        val maximum = visible.maxOf {
            maxOf(it.inputTokens, it.outputTokens, it.cachedTokens, it.reasoningTokens)
        }.coerceAtLeast(1)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            NumberAxis(maximum)
            Canvas(Modifier.weight(1f).height(220.dp).padding(horizontal = 3.dp)) {
                drawChartGrid(HealthGridColor)
                val groupWidth = size.width / visible.size.coerceAtLeast(1)
                val barWidth = (groupWidth * 0.16f).coerceAtMost(8.dp.toPx()).coerceAtLeast(1.dp.toPx())
                val gap = barWidth * 0.18f
                visible.forEachIndexed { index, point ->
                    val center = groupWidth * (index + 0.5f)
                    val values = listOf(point.inputTokens, point.outputTokens, point.cachedTokens, point.reasoningTokens)
                    val colors = listOf(InputTokenColor, OutputTokenColor, CachedTokenColor, ReasoningTokenColor)
                    val totalWidth = barWidth * 4 + gap * 3
                    values.forEachIndexed { valueIndex, value ->
                        val left = center - totalWidth / 2 + valueIndex * (barWidth + gap)
                        val top = size.height * (1f - value / maximum.toFloat())
                        drawRect(colors[valueIndex], topLeft = Offset(left, top), size = androidx.compose.ui.geometry.Size(barWidth, size.height - top))
                    }
                }
            }
        }
        TimeTicks(visible.map(MonitoringTimelineDto::bucketMs), endPadding = 4.dp)
    }
}

@Composable
private fun AnalyticsChartContainer(
    title: String,
    subtitle: String,
    modifier: Modifier,
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
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun LatencyAxis(maximum: Double) {
    Column(
        modifier = Modifier.width(48.dp).height(220.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        listOf(maximum, maximum / 2, 0.0).forEach {
            Text(it.asLatency(), style = MaterialTheme.typography.labelSmall, color = HealthLatencyColor)
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
    val indices = when {
        timestamps.isEmpty() -> emptyList()
        timestamps.size <= 3 -> timestamps.indices.toList()
        else -> listOf(0, timestamps.lastIndex / 2, timestamps.lastIndex)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 42.dp, end = endPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        indices.forEach { index ->
            Text(timestamps[index].chartDate(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawChartGrid(color: Color) {
    repeat(3) { row ->
        val y = size.height * row / 2f
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSmoothLine(points: List<Offset>, color: Color) {
    if (points.isEmpty()) return
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.zipWithNext().forEach { (previous, current) ->
            val midpoint = (previous.x + current.x) / 2f
            cubicTo(midpoint, previous.y, midpoint, current.y, current.x, current.y)
        }
    }
    drawPath(path, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun chartPosition(index: Int, count: Int, width: Float): Float =
    if (count <= 1) width / 2f else width * index / (count - 1f)

private fun Long.chartDate(): String = Instant.ofEpochMilli(this)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("MM/dd HH:mm"))

private val HealthSuccessColor = Color(0xFF55B938)
private val HealthFailureColor = Color(0xFFFF6B6B)
private val HealthLatencyColor = Color(0xFF11A8E2)
private val InputTokenColor = Color(0xFF5B9BF3)
private val OutputTokenColor = Color(0xFF24BE6B)
private val CachedTokenColor = Color(0xFF12A9BF)
private val ReasoningTokenColor = Color(0xFFF0A11A)
private val HealthGridColor = Color(0xFFD8E6F5)
