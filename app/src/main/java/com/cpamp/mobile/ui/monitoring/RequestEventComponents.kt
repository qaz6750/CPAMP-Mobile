package com.cpamp.mobile.ui.monitoring

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.width(5.dp).fillMaxHeight().background(
                    color = if (event.failed) MaterialTheme.colorScheme.error else SUCCESS_COLOR,
                    shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp),
                ),
            )
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        event.model.ifBlank { stringResource(R.string.unknown_model) },
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(event.timestampMs.asTime(), style = MaterialTheme.typography.labelSmall)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.event_tokens_value, event.totalTokens.compactTokens()),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    event.latencyMs?.let {
                        Text(
                            stringResource(R.string.event_latency_value, it.asLatency()),
                            style = MaterialTheme.typography.labelSmall,
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
internal fun RequestEventDetails(event: RequestEventDto) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                stringResource(R.string.request_details),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(event.timestampMs.asTime(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            DetailRow(
                stringResource(R.string.detail_status),
                if (event.failed) stringResource(R.string.failed) else stringResource(R.string.succeeded),
            )
        }
        item { DetailRow(stringResource(R.string.detail_model), event.model.ifBlank { "—" }) }
        item {
            DetailRow(
                stringResource(R.string.detail_endpoint),
                event.endpoint.ifBlank { event.path.ifBlank { "—" } },
            )
        }
        item {
            DetailRow(
                stringResource(R.string.detail_provider),
                event.authProviderSnapshot.ifBlank { event.source.ifBlank { "—" } },
            )
        }
        item {
            DetailRow(
                stringResource(R.string.detail_account),
                event.authLabelSnapshot.ifBlank { event.accountSnapshot.ifBlank { "—" } },
            )
        }
        item { DetailRow(stringResource(R.string.detail_tokens), event.totalTokens.compactTokens()) }
        item { DetailRow(stringResource(R.string.detail_latency), event.latencyMs?.asLatency() ?: "—") }
        if (event.failed) {
            item {
                DetailRow(
                    stringResource(R.string.detail_error),
                    SensitiveText.redact(event.failSummary).ifBlank { stringResource(R.string.request_failed) },
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyLarge)
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}

private val SUCCESS_COLOR = Color(0xFF2E7D5B)
