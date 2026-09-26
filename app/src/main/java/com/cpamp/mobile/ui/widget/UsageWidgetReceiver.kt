package com.cpamp.mobile.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback

class UsageWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UsageWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        UsageWidgetWork.schedulePeriodic(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        UsageWidgetWork.requestRefresh(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        UsageWidgetWork.cancel(context)
    }
}

class RefreshUsageWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        UsageWidgetWork.requestRefresh(context)
        UsageWidget.reload(context)
    }
}
