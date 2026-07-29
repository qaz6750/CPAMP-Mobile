package com.cpamp.mobile.ui.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cpamp.mobile.R
import com.cpamp.mobile.domain.model.ServerProfile
import com.cpamp.mobile.ui.common.safeServerName
import com.cpamp.mobile.ui.components.ConnectionPill

@Composable
internal fun ServerCard(
    profile: ServerProfile,
    active: Boolean,
    hideAddress: Boolean,
    onSwitch: () -> Unit,
    onDelete: () -> Unit,
) {
    val displayName = safeServerName(
        profile.name,
        profile.baseUrl,
        hideAddress,
        stringResource(R.string.system_servers),
    )
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))) {
        Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(displayName, fontWeight = FontWeight.SemiBold)
                    if (!hideAddress) {
                        Text(
                            profile.baseUrl,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                ConnectionPill(
                    label = stringResource(
                        when {
                            active -> R.string.active_server
                            profile.usesCleartext -> R.string.http_connection
                            else -> R.string.https_connection
                        },
                    ),
                    secure = !profile.usesCleartext,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (!active) {
                    TextButton(onClick = onSwitch) { Text(stringResource(R.string.switch_server)) }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.delete))
                }
            }
        }
    }
}
