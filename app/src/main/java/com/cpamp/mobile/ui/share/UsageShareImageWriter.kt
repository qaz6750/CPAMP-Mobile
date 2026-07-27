package com.cpamp.mobile.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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

        paint.color = BLUE
        canvas.drawRoundRect(RectF(56f, 56f, 148f, 148f), 20f, 20f, paint)
        paint.color = Color.WHITE
        paint.textSize = 42f
        paint.isFakeBoldText = true
        canvas.drawText("CP", 70f, 117f, paint)

        paint.color = TEXT
        paint.textSize = 42f
        canvas.drawText(context.getString(R.string.share_report_title), 172f, 103f, paint)
        paint.color = MUTED
        paint.textSize = 26f
        paint.isFakeBoldText = false
        canvas.drawText(report.rangeLabel(), 172f, 142f, paint)

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
            val top = 210f + row * 178f
            paint.color = CARD
            canvas.drawRoundRect(RectF(left, top, left + 466f, top + 150f), 20f, 20f, paint)
            paint.color = MUTED
            paint.textSize = 25f
            canvas.drawText(label, left + 28f, top + 48f, paint)
            paint.color = TEXT
            paint.textSize = 43f
            paint.isFakeBoldText = true
            canvas.drawText(value, left + 28f, top + 108f, paint)
            paint.isFakeBoldText = false
        }

        paint.color = TEXT
        paint.textSize = 31f
        paint.isFakeBoldText = true
        canvas.drawText(context.getString(R.string.usage_trend), 56f, 598f, paint)
        paint.isFakeBoldText = false
        drawTrend(canvas, paint, report.timeline, RectF(56f, 630f, 1024f, 872f))

        paint.color = TEXT
        paint.textSize = 31f
        paint.isFakeBoldText = true
        canvas.drawText(context.getString(R.string.top_models), 56f, 948f, paint)
        paint.isFakeBoldText = false
        if (report.topModels.isEmpty()) {
            paint.color = MUTED
            paint.textSize = 25f
            canvas.drawText(context.getString(R.string.no_traffic), 56f, 1000f, paint)
        } else {
            report.topModels.forEachIndexed { index, model ->
                val y = 1002f + index * 58f
                paint.color = TEXT
                paint.textSize = 25f
                canvas.drawText(model.name.take(38), 56f, y, paint)
                paint.color = MUTED
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText(
                    context.getString(R.string.share_model_value, model.requests.compactNumber(), model.tokens.compactNumber()),
                    1024f,
                    y,
                    paint,
                )
                paint.textAlign = Paint.Align.LEFT
            }
        }

        paint.color = MUTED
        paint.textSize = 22f
        canvas.drawText(context.getString(R.string.share_privacy_footer), 56f, 1310f, paint)
        return bitmap
    }

    private fun drawTrend(canvas: Canvas, paint: Paint, points: List<UsageSharePoint>, bounds: RectF) {
        paint.style = Paint.Style.FILL
        paint.color = CARD
        canvas.drawRoundRect(bounds, 20f, 20f, paint)
        if (points.isEmpty()) return
        val maximum = points.maxOf { it.tokens }.coerceAtLeast(1)
        val path = Path()
        points.forEachIndexed { index, point ->
            val x = if (points.size == 1) bounds.left else bounds.left + bounds.width() * index / points.lastIndex
            val y = bounds.bottom - 28f - (bounds.height() - 56f) * point.tokens / maximum.toFloat()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 8f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = BLUE
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
    }

    private fun UsageShareReport.rangeLabel(): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val zone = ZoneId.systemDefault()
        return "${Instant.ofEpochMilli(fromMs).atZone(zone).format(formatter)} - " +
            Instant.ofEpochMilli(toMs).atZone(zone).format(formatter)
    }

    private companion object {
        const val WIDTH = 1080
        const val HEIGHT = 1350
        val BACKGROUND = Color.rgb(246, 249, 255)
        val CARD = Color.WHITE
        val BLUE = Color.rgb(53, 106, 230)
        val TEXT = Color.rgb(24, 36, 58)
        val MUTED = Color.rgb(91, 105, 129)
    }
}