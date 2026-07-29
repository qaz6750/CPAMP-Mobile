package com.cpamp.mobile.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.cpamp.mobile.BuildConfig
import com.cpamp.mobile.R
import com.cpamp.mobile.ui.common.asLatency
import com.cpamp.mobile.ui.common.asCost
import com.cpamp.mobile.ui.common.asPercent
import com.cpamp.mobile.ui.common.compactNumber
import com.cpamp.mobile.ui.components.modelProviderVisual
import androidx.compose.ui.graphics.toArgb
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class UsageShareImageWriter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun write(report: UsageShareReport) = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "shared-reports").apply { mkdirs() }
        directory.listFiles()?.forEach(File::delete)
        val file = File(directory, "cpamp-usage-${report.fromMs}-${report.toMs}.png")
        FileOutputStream(file).use { output ->
            render(report).compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.files", file)
    }

    private fun render(report: UsageShareReport): Bitmap {
        val bitmap = Bitmap.createBitmap(OUTPUT_WIDTH, OUTPUT_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(BACKGROUND)
        canvas.scale(RENDER_SCALE, RENDER_SCALE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG).apply {
            isFilterBitmap = true
        }

        drawHeader(canvas, paint, report)
        drawMetrics(canvas, paint, report)
        drawTrendSection(canvas, paint, report)
        drawHealthSection(canvas, paint, report)
        drawTokenSection(canvas, paint, report)
        drawModels(canvas, paint, report)
        drawFooter(canvas, paint, report)
        return bitmap
    }

    private fun drawHeader(canvas: Canvas, paint: Paint, report: UsageShareReport) {
        paint.shader = LinearGradient(
            0f,
            0f,
            WIDTH.toFloat(),
            390f,
            intArrayOf(SKY_BLUE, BLUE),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(RectF(32f, 32f, 1048f, 390f), 36f, 36f, paint)
        paint.shader = null

        paint.color = Color.WHITE
        paint.textSize = 34f
        paint.isFakeBoldText = true
        canvas.drawText(context.getString(R.string.app_name), 74f, 108f, paint)
        paint.textSize = 44f
        canvas.drawText(context.getString(R.string.share_report_title), 74f, 166f, paint)

        paint.isFakeBoldText = false
        paint.textSize = 24f
        paint.color = Color.argb(225, 255, 255, 255)
        canvas.drawText(report.rangeLabel(), 74f, 212f, paint)

        paint.color = Color.argb(42, 255, 255, 255)
        canvas.drawRoundRect(RectF(64f, 232f, 280f, 310f), 39f, 39f, paint)
        paint.color = Color.WHITE
        paint.textSize = 27f
        paint.isFakeBoldText = true
        canvas.drawText(context.getString(R.string.share_days_badge, report.actualDays), 94f, 282f, paint)

        paint.isFakeBoldText = false
        paint.textSize = 24f
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(
            context.getString(
                R.string.trend_success_failure,
                report.successfulRequests,
                report.failedRequests,
            ),
            1016f,
            278f,
            paint,
        )
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawMetrics(canvas: Canvas, paint: Paint, report: UsageShareReport) {
        val metrics = listOf(
            context.getString(R.string.usage_requests) to report.requests.compactNumber(),
            context.getString(R.string.metric_success) to report.successRate.asPercent(),
            context.getString(R.string.usage_tokens) to report.tokens.compactNumber(),
            context.getString(R.string.usage_cost) to report.cost.asCost(),
        )
        metrics.forEachIndexed { index, (label, value) ->
            val column = index % 2
            val row = index / 2
            val left = 56f + column * 496f
            val top = 438f + row * 172f
            paint.color = CARD
            canvas.drawRoundRect(RectF(left, top, left + 466f, top + 148f), 24f, 24f, paint)
            paint.color = if (index == 1) GREEN else BLUE
            canvas.drawRoundRect(RectF(left, top, left + 9f, top + 148f), 5f, 5f, paint)
            paint.color = MUTED
            paint.textSize = 24f
            paint.isFakeBoldText = false
            canvas.drawText(label, left + 34f, top + 46f, paint)
            paint.color = TEXT
            paint.textSize = if (value.length > 10) 37f else 43f
            paint.isFakeBoldText = true
            canvas.drawText(value, left + 34f, top + 108f, paint)
        }
        paint.isFakeBoldText = false
    }

    private fun drawTrendSection(canvas: Canvas, paint: Paint, report: UsageShareReport) {
        paint.style = Paint.Style.FILL
        paint.pathEffect = null
        paint.color = TEXT
        paint.textSize = 32f
        paint.isFakeBoldText = true
        canvas.drawText(context.getString(R.string.share_trend_title), 56f, 812f, paint)
        paint.isFakeBoldText = false
        paint.color = MUTED
        paint.textSize = 21f
        canvas.drawText(context.getString(R.string.share_trend_subtitle), 56f, 846f, paint)

        drawLegend(canvas, paint, 600f, 812f, BLUE, context.getString(R.string.trend_requests))
        drawLegend(canvas, paint, 748f, 812f, GREEN, context.getString(R.string.trend_tokens))
        drawLegend(canvas, paint, 850f, 812f, ORANGE, context.getString(R.string.trend_cost))
        drawTrend(
            canvas = canvas,
            paint = paint,
            points = report.timeline,
            bounds = RectF(56f, 868f, 1024f, 1228f),
        )
    }

    private fun drawLegend(
        canvas: Canvas,
        paint: Paint,
        x: Float,
        y: Float,
        color: Int,
        label: String,
    ) {
        paint.color = color
        canvas.drawCircle(x, y - 7f, 7f, paint)
        paint.color = MUTED
        paint.textSize = 22f
        canvas.drawText(label, x + 16f, y, paint)
    }

    private fun drawTrend(
        canvas: Canvas,
        paint: Paint,
        points: List<UsageSharePoint>,
        bounds: RectF,
    ) {
        paint.style = Paint.Style.FILL
        paint.color = CARD
        canvas.drawRoundRect(bounds, 24f, 24f, paint)
        if (points.isEmpty() || points.none { it.requests > 0 || it.tokens > 0 || it.cost > 0 }) {
            paint.color = MUTED
            paint.textSize = 25f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(context.getString(R.string.share_no_traffic), bounds.centerX(), bounds.centerY(), paint)
            paint.textAlign = Paint.Align.LEFT
            return
        }

        val plot = RectF(bounds.left + 72f, bounds.top + 34f, bounds.right - 190f, bounds.bottom - 62f)
        val maxTokens = points.maxOf { it.tokens }.coerceAtLeast(1)
        val maxRequests = points.maxOf { it.requests }.coerceAtLeast(1)
        val maxCost = points.maxOf { it.cost }.coerceAtLeast(0.0001)
        repeat(4) { index ->
            val fraction = index / 3f
            val y = plot.top + plot.height() * fraction
            paint.color = GRID
            paint.strokeWidth = 2f
            canvas.drawLine(plot.left, y, plot.right, y, paint)
            paint.color = BLUE
            paint.textSize = 19f
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText((maxRequests * (1f - fraction)).toLong().compactNumber(), plot.left - 14f, y + 7f, paint)
            paint.color = GREEN
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText((maxTokens * (1f - fraction)).toLong().compactNumber(), plot.right + 14f, y + 7f, paint)
            paint.color = ORANGE
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText((maxCost * (1f - fraction)).asCost(), bounds.right - 14f, y + 7f, paint)
        }

        val requestPoints = points.mapIndexed { index, point ->
            chartPoint(index, points.size, point.requests.toDouble(), maxRequests.toDouble(), plot)
        }
        val tokenPoints = points.mapIndexed { index, point ->
            chartPoint(index, points.size, point.tokens.toDouble(), maxTokens.toDouble(), plot)
        }
        val costPoints = points.mapIndexed { index, point ->
            chartPoint(index, points.size, point.cost, maxCost, plot)
        }
        drawTrendArea(canvas, paint, requestPoints, plot, BLUE)
        drawTrendLine(canvas, paint, requestPoints, BLUE, showPoints = true)
        drawTrendLine(canvas, paint, tokenPoints, GREEN, showPoints = true)
        drawTrendLine(canvas, paint, costPoints, ORANGE, showPoints = true)
        paint.style = Paint.Style.FILL

        chartTicks(points).forEach { point ->
            val index = points.indexOf(point)
            val x = chartX(index, points.size, plot)
            paint.color = MUTED
            paint.textSize = 19f
            paint.textAlign = when (index) {
                0 -> Paint.Align.LEFT
                points.lastIndex -> Paint.Align.RIGHT
                else -> Paint.Align.CENTER
            }
            canvas.drawText(point.timestampMs.chartLabel(points), x, bounds.bottom - 22f, paint)
        }
        paint.textAlign = Paint.Align.LEFT
    }

    private fun chartPoint(index: Int, count: Int, value: Double, maximum: Double, plot: RectF): Pair<Float, Float> =
        chartX(index, count, plot) to (plot.bottom - plot.height() * (value / maximum).toFloat())

    private fun chartX(index: Int, count: Int, plot: RectF): Float =
        if (count <= 1) plot.centerX() else plot.left + plot.width() * index / (count - 1f)

    private fun drawTrendArea(
        canvas: Canvas,
        paint: Paint,
        points: List<Pair<Float, Float>>,
        plot: RectF,
        color: Int,
    ) {
        if (points.isEmpty()) return
        val path = smoothTrendPath(points).apply {
            lineTo(points.last().first, plot.bottom)
            lineTo(points.first().first, plot.bottom)
            close()
        }
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(24, Color.red(color), Color.green(color), Color.blue(color))
        canvas.drawPath(path, paint)
    }

    private fun drawTrendLine(
        canvas: Canvas,
        paint: Paint,
        points: List<Pair<Float, Float>>,
        color: Int,
        showPoints: Boolean,
    ) {
        if (points.isEmpty()) return
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 7f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = color
        canvas.drawPath(smoothTrendPath(points), paint)
        if (showPoints) {
            paint.style = Paint.Style.FILL
            val step = ((points.size - 1) / 4).coerceAtLeast(1)
            points.forEachIndexed { index, point ->
                if (index == 0 || index == points.lastIndex || index % step == 0) {
                    canvas.drawCircle(point.first, point.second, 6f, paint)
                }
            }
        }
        paint.style = Paint.Style.FILL
    }

    private fun smoothTrendPath(points: List<Pair<Float, Float>>) = Path().apply {
        moveTo(points.first().first, points.first().second)
        points.zipWithNext().forEach { (previous, current) ->
            val controlOffset = (current.first - previous.first) * 0.25f
            cubicTo(
                previous.first + controlOffset,
                previous.second,
                current.first - controlOffset,
                current.second,
                current.first,
                current.second,
            )
        }
    }

    private fun drawHealthSection(canvas: Canvas, paint: Paint, report: UsageShareReport) {
        paint.style = Paint.Style.FILL
        paint.pathEffect = null
        paint.color = TEXT
        paint.textSize = 32f
        paint.isFakeBoldText = true
        canvas.drawText(context.getString(R.string.request_health_trend), 56f, 1310f, paint)
        paint.isFakeBoldText = false
        paint.color = MUTED
        paint.textSize = 21f
        canvas.drawText(context.getString(R.string.request_health_subtitle), 56f, 1344f, paint)
        drawHealthChart(canvas, paint, report.timeline, RectF(56f, 1366f, 1024f, 1716f))
        drawLegend(canvas, paint, 170f, 1760f, HEALTH_SUCCESS, context.getString(R.string.health_success_rate))
        drawLegend(canvas, paint, 430f, 1760f, HEALTH_FAILURE, context.getString(R.string.health_failure_rate))
        drawLegend(canvas, paint, 690f, 1760f, HEALTH_LATENCY, context.getString(R.string.health_average_latency))
    }

    private fun drawHealthChart(
        canvas: Canvas,
        paint: Paint,
        points: List<UsageSharePoint>,
        bounds: RectF,
    ) {
        paint.style = Paint.Style.FILL
        paint.color = CARD
        canvas.drawRoundRect(bounds, 24f, 24f, paint)
        val hasData = points.any {
            it.successfulRequests > 0 || it.failedRequests > 0 || it.averageLatencyMs != null
        }
        if (!hasData) {
            drawEmptyChart(canvas, paint, bounds, context.getString(R.string.no_range_traffic))
            return
        }
        val plot = RectF(bounds.left + 78f, bounds.top + 30f, bounds.right - 108f, bounds.bottom - 58f)
        val maxLatency = points.maxOf { it.averageLatencyMs ?: 0.0 }.coerceAtLeast(1.0)
        repeat(4) { index ->
            val fraction = index / 3f
            val y = plot.top + plot.height() * fraction
            paint.style = Paint.Style.STROKE
            paint.color = GRID
            paint.strokeWidth = 2f
            paint.pathEffect = DashPathEffect(floatArrayOf(8f, 7f), 0f)
            canvas.drawLine(plot.left, y, plot.right, y, paint)
            paint.style = Paint.Style.FILL
            paint.pathEffect = null
            paint.textSize = 18f
            paint.color = MUTED
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("${((1f - fraction) * 100).toInt()}%", plot.left - 12f, y + 6f, paint)
            paint.color = HEALTH_LATENCY
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText((maxLatency * (1f - fraction)).asLatency(), plot.right + 12f, y + 6f, paint)
        }
        val groupWidth = plot.width() / points.size.coerceAtLeast(1)
        val barWidth = (groupWidth * 0.48f).coerceIn(2f, 16f)
        points.forEachIndexed { index, point ->
            val latency = point.averageLatencyMs ?: 0.0
            if (latency <= 0.0) return@forEachIndexed
            val center = categoryChartX(index, points.size, plot)
            val top = plot.bottom - plot.height() * (latency / maxLatency).toFloat()
            paint.color = Color.argb(164, Color.red(HEALTH_LATENCY), Color.green(HEALTH_LATENCY), Color.blue(HEALTH_LATENCY))
            canvas.drawRect(center - barWidth / 2f, top, center + barWidth / 2f, plot.bottom, paint)
        }
        val successPoints = points.mapIndexed { index, point ->
            categoryChartPoint(index, points.size, point.successRate, 1.0, plot)
        }
        val failurePoints = points.mapIndexed { index, point ->
            categoryChartPoint(index, points.size, point.failureRate, 1.0, plot)
        }
        drawTrendLine(canvas, paint, successPoints, HEALTH_SUCCESS, showPoints = false)
        drawTrendLine(canvas, paint, failurePoints, HEALTH_FAILURE, showPoints = false)
        drawTimeLabels(canvas, paint, points, plot, bounds.bottom - 18f, categoryAxis = true)
    }

    private fun drawTokenSection(canvas: Canvas, paint: Paint, report: UsageShareReport) {
        paint.style = Paint.Style.FILL
        paint.pathEffect = null
        paint.color = TEXT
        paint.textSize = 32f
        paint.isFakeBoldText = true
        canvas.drawText(context.getString(R.string.token_structure), 56f, 1800f, paint)
        paint.isFakeBoldText = false
        paint.color = MUTED
        paint.textSize = 21f
        canvas.drawText(context.getString(R.string.token_structure_subtitle), 56f, 1834f, paint)
        drawTokenChart(canvas, paint, report.timeline, RectF(56f, 1856f, 1024f, 2206f))
        drawLegend(canvas, paint, 100f, 2250f, TOKEN_INPUT, context.getString(R.string.token_input))
        drawLegend(canvas, paint, 350f, 2250f, TOKEN_OUTPUT, context.getString(R.string.token_output))
        drawLegend(canvas, paint, 600f, 2250f, TOKEN_CACHED, context.getString(R.string.token_cached))
        drawLegend(canvas, paint, 830f, 2250f, TOKEN_REASONING, context.getString(R.string.token_reasoning))
    }

    private fun drawTokenChart(
        canvas: Canvas,
        paint: Paint,
        points: List<UsageSharePoint>,
        bounds: RectF,
    ) {
        paint.style = Paint.Style.FILL
        paint.color = CARD
        canvas.drawRoundRect(bounds, 24f, 24f, paint)
        val hasData = points.any {
            it.inputTokens > 0 || it.outputTokens > 0 || it.cachedTokens > 0 || it.reasoningTokens > 0
        }
        if (!hasData) {
            drawEmptyChart(canvas, paint, bounds, context.getString(R.string.no_token_structure))
            return
        }
        val plot = RectF(bounds.left + 82f, bounds.top + 30f, bounds.right - 24f, bounds.bottom - 58f)
        val maximum = points.maxOf {
            it.inputTokens + it.outputTokens + it.cachedTokens + it.reasoningTokens
        }.coerceAtLeast(1)
        repeat(4) { index ->
            val fraction = index / 3f
            val y = plot.top + plot.height() * fraction
            paint.style = Paint.Style.STROKE
            paint.color = GRID
            paint.strokeWidth = 2f
            paint.pathEffect = DashPathEffect(floatArrayOf(8f, 7f), 0f)
            canvas.drawLine(plot.left, y, plot.right, y, paint)
            paint.style = Paint.Style.FILL
            paint.pathEffect = null
            paint.color = MUTED
            paint.textSize = 18f
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText((maximum * (1f - fraction)).toLong().compactNumber(), plot.left - 12f, y + 6f, paint)
        }
        val groupWidth = plot.width() / points.size.coerceAtLeast(1)
        val barWidth = (groupWidth * 0.64f).coerceIn(2f, 22f)
        points.forEachIndexed { index, point ->
            val center = categoryChartX(index, points.size, plot)
            val values = listOf(point.inputTokens, point.outputTokens, point.cachedTokens, point.reasoningTokens)
            val colors = listOf(TOKEN_INPUT, TOKEN_OUTPUT, TOKEN_CACHED, TOKEN_REASONING)
            var bottom = plot.bottom
            values.forEachIndexed { valueIndex, value ->
                if (value > 0) {
                    val segmentHeight = plot.height() * value / maximum.toFloat()
                    val top = bottom - segmentHeight
                    paint.color = colors[valueIndex]
                    canvas.drawRect(center - barWidth / 2f, top, center + barWidth / 2f, bottom, paint)
                    bottom = top
                }
            }
        }
        drawTimeLabels(canvas, paint, points, plot, bounds.bottom - 18f, categoryAxis = true)
    }

    private fun drawTimeLabels(
        canvas: Canvas,
        paint: Paint,
        points: List<UsageSharePoint>,
        plot: RectF,
        baseline: Float,
        categoryAxis: Boolean = false,
    ) {
        chartTicks(points).forEach { point ->
            val index = points.indexOf(point)
            val x = if (categoryAxis) categoryChartX(index, points.size, plot) else chartX(index, points.size, plot)
            paint.color = MUTED
            paint.textSize = 18f
            paint.textAlign = when (index) {
                0 -> Paint.Align.LEFT
                points.lastIndex -> Paint.Align.RIGHT
                else -> Paint.Align.CENTER
            }
            canvas.drawText(point.timestampMs.chartLabel(points), x, baseline, paint)
        }
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawEmptyChart(canvas: Canvas, paint: Paint, bounds: RectF, text: String) {
        paint.color = MUTED
        paint.textSize = 25f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(text, bounds.centerX(), bounds.centerY(), paint)
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawProviderIcon(canvas: Canvas, paint: Paint, model: String, centerX: Float, centerY: Float) {
        val provider = modelProviderVisual(model)
        val providerColor = provider.color.toArgb()
        paint.color = Color.argb(28, Color.red(providerColor), Color.green(providerColor), Color.blue(providerColor))
        canvas.drawCircle(centerX, centerY, 24f, paint)
        val drawable = provider.icon?.let { ContextCompat.getDrawable(context, it)?.mutate() }
        if (drawable != null) {
            DrawableCompat.setTint(drawable, providerColor)
            drawable.setBounds(
                (centerX - 17f).toInt(),
                (centerY - 17f).toInt(),
                (centerX + 17f).toInt(),
                (centerY + 17f).toInt(),
            )
            drawable.draw(canvas)
        } else {
            paint.color = providerColor
            paint.textSize = if (provider.badgeText == "xAI") 15f else 18f
            paint.isFakeBoldText = true
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(provider.badgeText.orEmpty(), centerX, centerY + 6f, paint)
            paint.textAlign = Paint.Align.LEFT
            paint.isFakeBoldText = false
        }
    }

    private fun drawModels(canvas: Canvas, paint: Paint, report: UsageShareReport) {
        paint.style = Paint.Style.FILL
        paint.pathEffect = null
        paint.color = TEXT
        paint.textSize = 32f
        paint.isFakeBoldText = true
        canvas.drawText(context.getString(R.string.share_top_models, report.shortRangeLabel()), 56f, 2310f, paint)
        paint.isFakeBoldText = false
        paint.color = CARD
        canvas.drawRoundRect(RectF(56f, 2340f, 1024f, 2780f), 24f, 24f, paint)

        if (report.topModels.isEmpty()) {
            paint.color = MUTED
            paint.textSize = 25f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(context.getString(R.string.share_no_traffic), 540f, 2566f, paint)
            paint.textAlign = Paint.Align.LEFT
            return
        }

        report.topModels.forEachIndexed { index, model ->
            val top = 2360f + index * 82f
            drawProviderIcon(canvas, paint, model.name, 94f, top + 34f)

            paint.color = TEXT
            paint.textSize = 25f
            val displayName = paint.ellipsize(model.name.ifBlank { context.getString(R.string.unknown_model) }, 500f)
            canvas.drawText(displayName, 136f, top + 31f, paint)
            paint.isFakeBoldText = false
            paint.color = MUTED
            paint.textSize = 21f
            canvas.drawText(
                context.getString(R.string.share_model_value, model.requests.compactNumber(), model.tokens.compactNumber()),
                136f,
                top + 62f,
                paint,
            )
            if (index < report.topModels.lastIndex) {
                paint.color = GRID
                canvas.drawRect(136f, top + 80f, 992f, top + 82f, paint)
            }
        }
    }

    private fun drawFooter(canvas: Canvas, paint: Paint, report: UsageShareReport) {
        paint.color = MUTED
        paint.textSize = 21f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(context.getString(R.string.share_privacy_footer), 56f, 2852f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(
            context.getString(R.string.share_generated_at, report.generatedAtMs.generatedLabel()),
            1024f,
            2852f,
            paint,
        )
        paint.textAlign = Paint.Align.LEFT
    }

    private fun UsageShareReport.rangeLabel(): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val zone = ZoneId.systemDefault()
        return "${Instant.ofEpochMilli(fromMs).atZone(zone).format(formatter)}  –  " +
            Instant.ofEpochMilli(toMs).atZone(zone).format(formatter)
    }

    private fun UsageShareReport.shortRangeLabel(): String {
        val formatter = DateTimeFormatter.ofPattern("MM-dd")
        val zone = ZoneId.systemDefault()
        val start = Instant.ofEpochMilli(fromMs).atZone(zone).format(formatter)
        val end = Instant.ofEpochMilli(toMs).atZone(zone).format(formatter)
        return if (start == end) start else "$start – $end"
    }

    private fun Long.chartLabel(points: List<UsageSharePoint>): String {
        val span = (points.lastOrNull()?.timestampMs ?: this) - (points.firstOrNull()?.timestampMs ?: this)
        val hourlyBuckets = points.zipWithNext().any { (previous, current) ->
            current.timestampMs - previous.timestampMs in 1 until DAY_MS
        }
        val pattern = when {
            span <= DAY_MS -> "HH:mm"
            hourlyBuckets -> "MM-dd HH:mm"
            else -> "MM-dd"
        }
        return Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern(pattern))
    }

    private fun Long.generatedLabel(): String = Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

    private fun Paint.ellipsize(value: String, maxWidth: Float): String {
        if (measureText(value) <= maxWidth) return value
        val suffix = "…"
        val count = breakText(value, true, (maxWidth - measureText(suffix)).coerceAtLeast(0f), null)
        return value.take(count.coerceAtLeast(0)) + suffix
    }

    private fun chartTicks(points: List<UsageSharePoint>): List<UsageSharePoint> {
        if (points.size <= 5) return points
        return (0..4).map { step -> points[points.lastIndex * step / 4] }.distinct()
    }

    private fun categoryChartPoint(
        index: Int,
        count: Int,
        value: Double,
        maximum: Double,
        plot: RectF,
    ): Pair<Float, Float> =
        categoryChartX(index, count, plot) to (plot.bottom - plot.height() * (value / maximum).toFloat())

    private fun categoryChartX(index: Int, count: Int, plot: RectF): Float =
        if (count <= 1) plot.centerX() else plot.left + plot.width() * (index + 0.5f) / count

    private companion object {
        const val WIDTH = 1080
        const val HEIGHT = 2900
        const val RENDER_SCALE = 1.5f
        const val OUTPUT_WIDTH = 1620
        const val OUTPUT_HEIGHT = 4350
        const val DAY_MS = 24 * 60 * 60 * 1000L
        val BACKGROUND = Color.rgb(239, 248, 255)
        val CARD = Color.WHITE
        val SKY_BLUE = Color.rgb(56, 189, 248)
        val BLUE = Color.rgb(64, 158, 255)
        val GREEN = Color.rgb(20, 184, 166)
        val ORANGE = Color.rgb(245, 158, 11)
        val HEALTH_SUCCESS = Color.rgb(103, 194, 58)
        val HEALTH_FAILURE = Color.rgb(245, 108, 108)
        val HEALTH_LATENCY = Color.rgb(14, 165, 233)
        val TOKEN_INPUT = Color.rgb(96, 165, 250)
        val TOKEN_OUTPUT = Color.rgb(34, 197, 94)
        val TOKEN_CACHED = Color.rgb(6, 182, 212)
        val TOKEN_REASONING = Color.rgb(245, 158, 11)
        val TEXT = Color.rgb(15, 41, 66)
        val MUTED = Color.rgb(89, 112, 136)
        val GRID = Color.rgb(222, 235, 246)
    }
}
