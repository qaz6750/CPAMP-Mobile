package com.cpamp.mobile.ui.monitoring

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cpamp.mobile.R
import com.cpamp.mobile.data.remote.model.RequestEventDto
import com.cpamp.mobile.ui.common.SensitiveText
import com.cpamp.mobile.ui.common.asLatency
import com.cpamp.mobile.ui.common.asTime
import com.cpamp.mobile.ui.common.compactTokens

@Composable
internal fun RequestEventCard(event: RequestEventDto, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.width(5.dp).fillMaxHeight().background(
                    color = if (event.failed) MaterialTheme.colorScheme.error else SUCCESS_ACCENT_COLOR,
                    shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp),
                ),
            )
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        event.model.ifBlank { stringResource(R.string.unknown_model) },
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(event.timestampMs.asTime(), style = MaterialTheme.typography.labelSmall)
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        stringResource(R.string.event_tokens_value, event.totalTokens.compactTokens()),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    event.latencyMs?.let {
                        Text(
                            stringResource(R.string.event_latency_value, it.asLatency()),
                            style = MaterialTheme.typography.labelSmall,
                            color = requestDurationColor(it, LATENCY_WARNING_MS, LATENCY_CRITICAL_MS),
                        )
                    }
                    event.ttftMs?.let {
                        Text(
                            stringResource(R.string.event_ttft_value, it.asLatency()),
                            style = MaterialTheme.typography.labelSmall,
                            color = requestDurationColor(it, TTFT_WARNING_MS, TTFT_CRITICAL_MS),
                        )
                    }
                    event.failStatusCode?.let {
                        Text(
                            "HTTP $it",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun RequestEventDetailsTitle(event: RequestEventDto) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.request_details),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                event.timestampMs.asTime(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                color = if (event.failed) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(
                    if (event.failed) stringResource(R.string.failed) else stringResource(R.string.succeeded),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
internal fun RequestEventDetails(event: RequestEventDto) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(0.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { DetailRow(stringResource(R.string.detail_model), event.model.ifBlank { "—" }) }
        item { DetailRow(stringResource(R.string.detail_service), event.source.ifBlank { "—" }) }
        item { DetailRow(stringResource(R.string.detail_provider), event.authProviderSnapshot.ifBlank { "—" }) }
        item {
            DetailRow(
                stringResource(R.string.detail_account),
                event.authLabelSnapshot.ifBlank { event.accountSnapshot.ifBlank { "—" } },
            )
        }
        item { DetailRow(stringResource(R.string.detail_tokens), event.totalTokens.compactTokens()) }
        item { DetailRow(stringResource(R.string.detail_reasoning), event.reasoningTokens.compactTokens()) }
        item {
            DetailRow(
                stringResource(R.string.detail_ttft),
                event.ttftMs?.asLatency() ?: "—",
                event.ttftMs?.let { requestDurationColor(it, TTFT_WARNING_MS, TTFT_CRITICAL_MS) }
                    ?: MaterialTheme.colorScheme.onSurface,
            )
        }
        item {
            DetailRow(
                stringResource(R.string.detail_latency),
                event.latencyMs?.asLatency() ?: "—",
                event.latencyMs?.let { requestDurationColor(it, LATENCY_WARNING_MS, LATENCY_CRITICAL_MS) }
                    ?: MaterialTheme.colorScheme.onSurface,
            )
        }
        if (event.failed) {
            item {
                DetailRow(
                    stringResource(R.string.detail_error),
                    SensitiveText.redact(event.failSummary).ifBlank { stringResource(R.string.request_failed) },
                    valueColor = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            modifier = Modifier.weight(0.8f),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            modifier = Modifier.weight(1.2f),
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun requestDurationColor(durationMs: Long, warningMs: Long, criticalMs: Long): Color = when {
    durationMs < warningMs -> SUCCESS_COLOR
    durationMs < criticalMs -> WARNING_COLOR
    else -> MaterialTheme.colorScheme.error
}

private val SUCCESS_COLOR = Color(0xFF2E7D5B)
private val SUCCESS_ACCENT_COLOR = Color(0xFF4CAF7A)
private val WARNING_COLOR = Color(0xFFF59E0B)
private const val TTFT_WARNING_MS = 800L
private const val TTFT_CRITICAL_MS = 2_000L
private const val LATENCY_WARNING_MS = 2_000L
private const val LATENCY_CRITICAL_MS = 5_000L
