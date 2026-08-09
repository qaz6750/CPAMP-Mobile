package com.cpamp.mobile.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cpamp.mobile.R
import com.cpamp.mobile.domain.model.AuthenticatedSession
import com.cpamp.mobile.domain.model.ServerProfile
import com.cpamp.mobile.ui.common.safeServerName
import com.cpamp.mobile.ui.components.ConnectionPill

@Composable
internal fun ServerManagementSettings(
    session: AuthenticatedSession,
    profiles: List<ServerProfile>,
    hideAddresses: Boolean,
    onSwitchServer: (String) -> Unit,
    onDeleteServer: (String) -> Unit,
    onDisconnect: () -> Unit,
) {
    var deleteProfile by remember { mutableStateOf<ServerProfile?>(null) }

    SettingsCard(stringResource(R.string.system_servers)) {
        Text(
            stringResource(R.string.saved_servers_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        profiles.forEachIndexed { index, profile ->
            if (index > 0) HorizontalDivider()
            ServerSettingsRow(
                profile = profile,
                active = profile.id == session.profile.id,
                hideAddress = hideAddresses,
                onSwitch = { onSwitchServer(profile.id) },
                onDelete = { deleteProfile = profile },
            )
        }
        OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
            Text(stringResource(R.string.disconnect), modifier = Modifier.padding(start = 8.dp))
        }
    }

    deleteProfile?.let { profile ->
        val fallback = stringResource(R.string.system_servers)
        AlertDialog(
            onDismissRequest = { deleteProfile = null },
            title = { Text(stringResource(R.string.delete_server_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.delete_server_body,
                        safeServerName(profile.name, profile.baseUrl, hideAddresses, fallback),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteProfile = null
                        onDeleteServer(profile.id)
                    },
                ) {
                    Text(stringResource(R.string.confirm_change))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteProfile = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ServerSettingsRow(
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(displayName, fontWeight = FontWeight.SemiBold)
                if (!hideAddress) {
                    Text(
                        profile.baseUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (!active) {
                TextButton(onClick = onSwitch) { Text(stringResource(R.string.switch_server)) }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.delete))
            }
        }
    }
}