package com.cpamp.mobile.ui.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cpamp.mobile.R
import com.cpamp.mobile.domain.model.AuthenticatedSession
import com.cpamp.mobile.ui.common.asTime
import com.cpamp.mobile.ui.common.safeServerName
import com.cpamp.mobile.ui.components.ConnectionPill

@Composable
internal fun ConnectionCard(session: AuthenticatedSession, hideAddress: Boolean) {
    val displayName = safeServerName(
        session.profile.name,
        session.profile.baseUrl,
        hideAddress,
        stringResource(R.string.system_servers),
    )
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(displayName, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                ConnectionPill(
                    label = stringResource(
                        if (session.profile.usesCleartext) R.string.http_connection else R.string.https_connection,
                    ),
                    secure = !session.profile.usesCleartext,
                )
            }
            if (!hideAddress) {
                Text(session.profile.baseUrl, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                stringResource(R.string.last_connected, session.profile.lastConnectedAt.asTime()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun ManagerStatusCard(state: SystemUiState) {
    val status = state.status
    val info = state.info
    StatusCard(stringResource(R.string.system_status)) {
        StatusRow(
            stringResource(R.string.service_name),
            info?.service.orEmpty().ifBlank { stringResource(R.string.unknown_value) },
        )
        StatusRow(
            stringResource(R.string.service_mode),
            info?.mode.orEmpty().ifBlank { stringResource(R.string.unknown_value) },
        )
        StatusRow(stringResource(R.string.cpa_version), state.cpaVersion ?: stringResource(R.string.not_provided))
        StatusRow(stringResource(R.string.cpamp_version), state.cpampVersion ?: stringResource(R.string.not_provided))
        StatusRow(stringResource(R.string.event_count_label), status?.events?.toString() ?: "—")
        StatusRow(stringResource(R.string.dead_letters), status?.deadLetters?.toString() ?: "—")
        StatusRow(
            stringResource(R.string.configuration_state),
            stringResource(if (info?.configured == true) R.string.configured else R.string.not_configured),
        )
    }
}

@Composable
internal fun CollectorStatusCard(state: SystemUiState) {
    val collector = state.status?.collector
    StatusCard(stringResource(R.string.collector_status)) {
        StatusRow(
            stringResource(R.string.collector_running),
            stringResource(if (collector?.running == true) R.string.running else R.string.stopped),
        )
        StatusRow(stringResource(R.string.collector_mode), collector?.mode.orEmpty().ifBlank { "—" })
        if ((collector?.lastEventAt ?: 0) > 0) {
            StatusRow(stringResource(R.string.last_event), requireNotNull(collector).lastEventAt.asTime())
        }
        if (!collector?.error.isNullOrBlank()) {
            Text(
                collector?.error.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun StatusCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
