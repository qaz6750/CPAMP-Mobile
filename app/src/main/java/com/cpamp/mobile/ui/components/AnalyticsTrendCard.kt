package com.cpamp.mobile.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.dp
import com.cpamp.mobile.R
import com.cpamp.mobile.ui.common.compactNumber
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class AnalyticsTrendPoint(
    val timestampMs: Long,
    val requests: Long,
    val tokens: Long,
)

private enum class TrendMetric { Tokens, Requests }

@Composable
fun AnalyticsTrendCard(
    title: String,
    points: List<AnalyticsTrendPoint>,
    emptyText: String,
    modifier: Modifier = Modifier,
) {
    var metric by remember { mutableStateOf(TrendMetric.Tokens) }
    val values = points.map { if (metric == TrendMetric.Tokens) it.tokens else it.requests }
    val maximum = values.maxOrNull().orEmptyMaximum()
    val chartDescription = stringResource(
        if (metric == TrendMetric.Tokens) R.string.token_trend_description else R.string.request_trend_description,
        values.sum(),
    )
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = metric == TrendMetric.Tokens,
                        onClick = { metric = TrendMetric.Tokens },
                        label = { Text(stringResource(R.string.trend_tokens)) },
                    )
                    FilterChip(
                        selected = metric == TrendMetric.Requests,
                        onClick = { metric = TrendMetric.Requests },
                        label = { Text(stringResource(R.string.trend_requests)) },
                    )
                }
            }
            if (points.isEmpty() || maximum == 0L) {
                Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                    Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val lineColor = MaterialTheme.colorScheme.primary
                val gridColor = MaterialTheme.colorScheme.outlineVariant
                Row(Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.width(42.dp).height(150.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.End,
                    ) {
                        Text(maximum.compactNumber(), style = MaterialTheme.typography.labelSmall)
                        Text((maximum / 2).compactNumber(), style = MaterialTheme.typography.labelSmall)
                        Text("0", style = MaterialTheme.typography.labelSmall)
                    }
                    Canvas(
                        modifier = Modifier.weight(1f).height(150.dp).padding(start = 8.dp)
                            .semantics { contentDescription = chartDescription },
                    ) {
                        repeat(3) { row ->
                            val y = size.height * row / 2f
                            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                        }
                        drawLine(gridColor, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 2f)
                        drawLine(gridColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 2f)
                        val path = Path()
                        values.forEachIndexed { index, value ->
                            val x = if (values.size == 1) 0f else size.width * index / (values.size - 1f)
                            val y = size.height - size.height * value / maximum.toFloat()
                            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(path, lineColor, style = Stroke(width = 5f, cap = StrokeCap.Round))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(start = 50.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    chartTicks(points).forEach { point ->
                        Text(point.timestampMs.asChartTime(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun Long?.orEmptyMaximum(): Long = this?.coerceAtLeast(0) ?: 0

private fun chartTicks(points: List<AnalyticsTrendPoint>): List<AnalyticsTrendPoint> = when (points.size) {
    0 -> emptyList()
    1 -> listOf(points.first())
    2 -> points
    else -> listOf(points.first(), points[points.lastIndex / 2], points.last())
}

private fun Long.asChartTime(): String = Instant.ofEpochMilli(this)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("HH:mm"))