package com.cpamp.mobile.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.cpamp.mobile.MainActivity
import com.cpamp.mobile.R
import com.cpamp.mobile.common.SECONDS_PER_DAY
import com.cpamp.mobile.common.SECONDS_PER_HOUR
import com.cpamp.mobile.data.widget.UsageWidgetCache
import com.cpamp.mobile.data.widget.UsageWidgetCredential
import com.cpamp.mobile.data.widget.UsageWidgetError
import com.cpamp.mobile.data.widget.UsageWidgetQuotaWindow
import com.cpamp.mobile.data.widget.UsageWidgetState
import com.cpamp.mobile.data.widget.UsageWidgetUsage
import com.cpamp.mobile.ui.accounts.providerBadgeLabel
import com.cpamp.mobile.ui.common.asCost
import com.cpamp.mobile.ui.common.asPercent
import com.cpamp.mobile.ui.common.asTime
import com.cpamp.mobile.ui.common.compactNumber
import com.cpamp.mobile.ui.common.compactTokens
import java.util.Locale
import kotlin.math.roundToInt

class UsageWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = context.usageWidgetRepository()
        val initial = repository.state()
        provideContent {
            val revision = currentState<Preferences>()[RevisionKey] ?: 0L
            val state by produceState(initial, revision) { value = repository.state() }
            GlanceTheme {
                UsageWidgetContent(state)
            }
        }
    }

    companion object {
        private val RevisionKey = longPreferencesKey("usage_widget_revision")

        suspend fun reload(context: Context) {
            val revision = System.nanoTime()
            GlanceAppWidgetManager(context).getGlanceIds(UsageWidget::class.java).forEach { id ->
                updateAppWidgetState(context, id) { preferences -> preferences[RevisionKey] = revision }
                UsageWidget().update(context, id)
            }
        }
    }
}

@Composable
private fun UsageWidgetContent(state: UsageWidgetState) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Header(state)
        Spacer(GlanceModifier.height(6.dp))
        when (state) {
            UsageWidgetState.Locked -> Message(context.getString(R.string.widget_locked))
            UsageWidgetState.NotConnected -> Message(context.getString(R.string.widget_not_connected))
            is UsageWidgetState.Ready -> ReadyContent(state.cache)
        }
    }
}

@Composable
private fun Header(state: UsageWidgetState) {
    val context = LocalContext.current
    val status = (state as? UsageWidgetState.Ready)?.let { ready ->
        val updatedAtMs = ready.cache.usage?.updatedAtMs ?: ready.cache.quotaUpdatedAtMs
        when {
            ready.refreshing -> context.getString(R.string.widget_refreshing)
            updatedAtMs > 0 -> context.getString(R.string.widget_updated, updatedAtMs.asTime())
            else -> null
        }
    }
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = context.getString(R.string.widget_title),
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        status?.let {
            Text(
                text = it,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                maxLines = 1,
            )
        }
        if (state is UsageWidgetState.Ready) {
            Box(
                modifier = GlanceModifier
                    .size(40.dp)
                    .clickable(actionRunCallback<RefreshUsageWidgetAction>()),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_refresh),
                    contentDescription = context.getString(R.string.widget_refresh),
                    modifier = GlanceModifier.size(20.dp),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface),
                )
            }
        }
    }
}

@Composable
private fun ReadyContent(cache: UsageWidgetCache) {
    val context = LocalContext.current
    cache.lastError?.let { error ->
        Text(
            text = context.getString(error.messageResource()),
            style = TextStyle(color = GlanceTheme.colors.error, fontSize = 11.sp),
            maxLines = 2,
        )
        Spacer(GlanceModifier.height(4.dp))
    }
    val usage = cache.usage
    if (usage == null) {
        if (cache.lastError == null) Message(context.getString(R.string.widget_no_data))
    } else {
        UsageMetrics(usage)
    }
    val credentials = cache.credentials ?: return
    Spacer(GlanceModifier.height(8.dp))
    Text(
        text = context.getString(R.string.widget_quota_title),
        style = TextStyle(
            color = GlanceTheme.colors.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        ),
    )
    if (credentials.isEmpty()) {
        Message(context.getString(R.string.widget_quota_empty))
    } else {
        LazyColumn(modifier = GlanceModifier.fillMaxWidth()) {
            items(credentials, itemId = { it.ordinal.toLong() }) { credential ->
                CredentialRow(credential)
            }
        }
    }
}

@Composable
private fun UsageMetrics(usage: UsageWidgetUsage) {
    val context = LocalContext.current
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        Metric(usage.calls.compactNumber(), context.getString(R.string.widget_metric_calls))
        Metric(usage.successRate.asPercent(), context.getString(R.string.widget_metric_success))
        Metric(usage.tokens.compactNumber(), context.getString(R.string.widget_metric_tokens))
        Metric(usage.cost.asCost(), context.getString(R.string.widget_metric_cost))
        Metric(
            String.format(Locale.US, "%.1f", usage.rpm),
            context.getString(R.string.widget_metric_rpm),
        )
    }
}

@Composable
private fun RowScope.Metric(value: String, label: String) {
    Column(modifier = GlanceModifier.defaultWeight()) {
        Text(
            text = value,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
        Text(
            text = label,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
            maxLines = 1,
        )
    }
}

@Composable
private fun CredentialRow(credential: UsageWidgetCredential) {
    val context = LocalContext.current
    val lowest = credential.windows.minOf(UsageWidgetQuotaWindow::remainingPercent)
    val title = context.getString(
        R.string.widget_credential_title,
        providerBadgeLabel(credential.provider),
        credential.ordinal,
    ).let { base ->
        if (credential.planType.isBlank()) base
        else context.getString(R.string.widget_credential_plan, base, credential.planType)
    }
    Column(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                text = title,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(
                text = credential.windows.joinToString("  ") { window ->
                    context.getString(
                        R.string.widget_quota_window,
                        window.compactLabel(context),
                        window.remainingPercent.roundToInt(),
                    )
                },
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                maxLines = 1,
            )
        }
        LinearProgressIndicator(
            progress = (lowest / 100.0).coerceIn(0.0, 1.0).toFloat(),
            modifier = GlanceModifier.fillMaxWidth().height(4.dp),
            color = if (lowest < LOW_QUOTA_PERCENT) GlanceTheme.colors.error else GlanceTheme.colors.primary,
            backgroundColor = GlanceTheme.colors.surfaceVariant,
        )
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text = text,
        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
        maxLines = 3,
    )
}

private fun UsageWidgetQuotaWindow.compactLabel(context: Context): String = when {
    durationSeconds > 0 && durationSeconds % SECONDS_PER_DAY == 0L ->
        context.getString(R.string.accounts_quota_compact_days, durationSeconds / SECONDS_PER_DAY)
    durationSeconds > 0 && durationSeconds % SECONDS_PER_HOUR == 0L ->
        context.getString(R.string.accounts_quota_compact_hours, durationSeconds / SECONDS_PER_HOUR)
    else -> context.getString(R.string.credential_quota_window_other)
}

private fun UsageWidgetError.messageResource(): Int = when (this) {
    UsageWidgetError.NotConnected -> R.string.widget_not_connected
    UsageWidgetError.KeyUnavailable -> R.string.widget_error_key
    UsageWidgetError.Unauthorized -> R.string.widget_error_unauthorized
    UsageWidgetError.RateLimited -> R.string.widget_error_rate_limited
    UsageWidgetError.Network -> R.string.widget_error_network
    UsageWidgetError.Server -> R.string.widget_error_server
}

private const val LOW_QUOTA_PERCENT = 20.0
