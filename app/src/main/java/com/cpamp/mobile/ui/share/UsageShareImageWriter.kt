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
import kotlin.math.min

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
        paint.textSize = 46f
        paint.isFakeBoldText = true
        canvas.drawText("CP", 74f, 122f, paint)
        paint.textSize = 44f
        canvas.drawText(context.getString(R.string.share_report_title), 164f, 122f, paint)

        paint.isFakeBoldText = false
        paint.textSize = 27f
        paint.color = Color.argb(225, 255, 255, 255)
        canvas.drawText(report.rangeLabel(), 64f, 190f, paint)

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
        canvas.drawText(context.getString(R.string.usage_trend), 56f, 824f, paint)
        paint.isFakeBoldText = false

        drawLegend(canvas, paint, 720f, 816f, BLUE, context.getString(R.string.trend_requests))
        drawLegend(canvas, paint, 866f, 816f, GREEN, context.getString(R.string.trend_tokens))
        drawTrend(
            canvas = canvas,
            paint = paint,
            points = report.timeline,
            bounds = RectF(56f, 850f, 1024f, 1228f),
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
        if (points.isEmpty() || points.none { it.requests > 0 || it.tokens > 0 }) {
            paint.color = MUTED
            paint.textSize = 25f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(context.getString(R.string.no_traffic), bounds.centerX(), bounds.centerY(), paint)
            paint.textAlign = Paint.Align.LEFT
            return
        }

        val plot = RectF(bounds.left + 76f, bounds.top + 36f, bounds.right - 54f, bounds.bottom - 62f)
        val maxTokens = points.maxOf { it.tokens }.coerceAtLeast(1)
        val maxRequests = points.maxOf { it.requests }.coerceAtLeast(1)
        repeat(3) { index ->
            val y = plot.top + plot.height() * index / 2f
            paint.color = GRID
            paint.strokeWidth = 2f
            canvas.drawLine(plot.left, y, plot.right, y, paint)
            paint.color = MUTED
            paint.textSize = 19f
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(((maxTokens * (2 - index)) / 2).compactNumber(), plot.left - 14f, y + 7f, paint)
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(((maxRequests * (2 - index)) / 2).compactNumber(), plot.right + 14f, y + 7f, paint)
        }

        val slotWidth = plot.width() / points.size
        val barWidth = min(slotWidth * 0.5f, 24f)
        points.forEachIndexed { index, point ->
            val x = plot.left + slotWidth * (index + 0.5f)
            val top = plot.bottom - plot.height() * point.tokens / maxTokens.toFloat()
            paint.color = Color.argb(210, 16, 185, 129)
            canvas.drawRoundRect(RectF(x - barWidth / 2f, top, x + barWidth / 2f, plot.bottom), 5f, 5f, paint)
        }

        val requestPath = Path()
        val areaPath = Path()
        points.forEachIndexed { index, point ->
            val x = plot.left + slotWidth * (index + 0.5f)
            val y = plot.bottom - plot.height() * point.requests / maxRequests.toFloat()
            if (index == 0) {
                requestPath.moveTo(x, y)
                areaPath.moveTo(x, plot.bottom)
                areaPath.lineTo(x, y)
            } else {
                requestPath.lineTo(x, y)
                areaPath.lineTo(x, y)
            }
        }
        areaPath.lineTo(plot.left + slotWidth * (points.size - 0.5f), plot.bottom)
        areaPath.close()
        paint.shader = LinearGradient(
            0f,
            plot.top,
            0f,
            plot.bottom,
            BLUE_FADE,
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP,
        )
        paint.style = Paint.Style.FILL
        canvas.drawPath(areaPath, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 7f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = BLUE
        canvas.drawPath(requestPath, paint)
        paint.style = Paint.Style.FILL

        chartTicks(points).forEach { point ->
            val index = points.indexOf(point)
            val x = plot.left + slotWidth * (index + 0.5f)
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

    private fun drawModels(canvas: Canvas, paint: Paint, report: UsageShareReport) {
        paint.color = TEXT
        paint.textSize = 32f
        paint.isFakeBoldText = true
        canvas.drawText(context.getString(R.string.top_models), 56f, 1310f, paint)
        paint.isFakeBoldText = false
        paint.color = CARD
        canvas.drawRoundRect(RectF(56f, 1340f, 1024f, 1780f), 24f, 24f, paint)

        if (report.topModels.isEmpty()) {
            paint.color = MUTED
            paint.textSize = 25f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(context.getString(R.string.no_traffic), 540f, 1566f, paint)
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

    private fun chartTicks(points: List<UsageSharePoint>): List<UsageSharePoint> = when (points.size) {
        0 -> emptyList()
        1 -> listOf(points.first())
        2 -> points
        else -> listOf(points.first(), points[points.lastIndex / 2], points.last()).distinct()
    }

    private companion object {
        const val WIDTH = 1080
        const val HEIGHT = 1900
        val BACKGROUND = Color.rgb(239, 248, 255)
        val CARD = Color.WHITE
        val SKY_BLUE = Color.rgb(56, 189, 248)
        val BLUE = Color.rgb(14, 165, 233)
        val BLUE_FADE = Color.argb(70, 14, 165, 233)
        val GREEN = Color.rgb(16, 185, 129)
        val TEXT = Color.rgb(15, 41, 66)
        val MUTED = Color.rgb(89, 112, 136)
        val GRID = Color.rgb(222, 235, 246)
    }
}
