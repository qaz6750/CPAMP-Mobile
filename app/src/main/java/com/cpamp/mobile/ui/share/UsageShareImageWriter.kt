package com.cpamp.mobile.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.content.FileProvider
import com.cpamp.mobile.BuildConfig
import com.cpamp.mobile.R
import com.cpamp.mobile.ui.common.asCost
import com.cpamp.mobile.ui.common.asPercent
import com.cpamp.mobile.ui.common.compactNumber
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
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(BACKGROUND)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        drawHeader(canvas, paint, report)
        drawMetrics(canvas, paint, report)
        drawTrendSection(canvas, paint, report)
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
            useHourLabels = report.actualDays <= 1,
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
        useHourLabels: Boolean,
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
            canvas.drawText(point.timestampMs.chartLabel(useHourLabels), x, bounds.bottom - 22f, paint)
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
    }

    private fun smoothTrendPath(points: List<Pair<Float, Float>>) = Path().apply {
        moveTo(points.first().first, points.first().second)
        points.zipWithNext().forEach { (previous, current) ->
            val midpoint = (previous.first + current.first) / 2f
            cubicTo(midpoint, previous.second, midpoint, current.second, current.first, current.second)
        }
    }

    private fun drawModels(canvas: Canvas, paint: Paint, report: UsageShareReport) {
        paint.color = TEXT
        paint.textSize = 32f
        paint.isFakeBoldText = true
        canvas.drawText(context.getString(R.string.share_top_models, report.shortRangeLabel()), 56f, 1310f, paint)
        paint.isFakeBoldText = false
        paint.color = CARD
        canvas.drawRoundRect(RectF(56f, 1340f, 1024f, 1780f), 24f, 24f, paint)

        if (report.topModels.isEmpty()) {
            paint.color = MUTED
            paint.textSize = 25f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(context.getString(R.string.share_no_traffic), 540f, 1566f, paint)
            paint.textAlign = Paint.Align.LEFT
            return
        }

        report.topModels.forEachIndexed { index, model ->
            val top = 1360f + index * 82f
            paint.color = if (index == 0) BLUE else SKY_BLUE
            canvas.drawCircle(94f, top + 34f, 22f, paint)
            paint.color = Color.WHITE
            paint.textSize = 21f
            paint.isFakeBoldText = true
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText((index + 1).toString(), 94f, top + 42f, paint)
            paint.textAlign = Paint.Align.LEFT

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
        canvas.drawText(context.getString(R.string.share_privacy_footer), 56f, 1852f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(
            context.getString(R.string.share_generated_at, report.generatedAtMs.generatedLabel()),
            1024f,
            1852f,
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

    private fun Long.chartLabel(reportUsesHours: Boolean): String {
        val pattern = if (reportUsesHours) "HH:mm" else "MM-dd"
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

    private companion object {
        const val WIDTH = 1080
        const val HEIGHT = 1900
        val BACKGROUND = Color.rgb(239, 248, 255)
        val CARD = Color.WHITE
        val SKY_BLUE = Color.rgb(56, 189, 248)
        val BLUE = Color.rgb(14, 165, 233)
        val GREEN = Color.rgb(16, 185, 129)
        val ORANGE = Color.rgb(245, 158, 11)
        val TEXT = Color.rgb(15, 41, 66)
        val MUTED = Color.rgb(89, 112, 136)
        val GRID = Color.rgb(222, 235, 246)
    }
}
