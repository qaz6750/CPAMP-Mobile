package com.cpamp.mobile.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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

    SettingsGroupCard(
        title = stringResource(R.string.system_servers),
        subtitle = stringResource(R.string.saved_servers_help),
    ) {
        profiles.forEachIndexed { index, profile ->
            ServerSettingsRow(
                profile = profile,
                active = profile.id == session.profile.id,
                hideAddress = hideAddresses,
                onSwitch = { onSwitchServer(profile.id) },
                onDelete = { deleteProfile = profile },
            )
            if (index < profiles.lastIndex) {
                SettingsDivider()
            }
        }
        if (profiles.isNotEmpty()) {
            SettingsDivider()
        }
        Box(Modifier.fillMaxWidth().padding(12.dp)) {
            OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
                Text(stringResource(R.string.disconnect), modifier = Modifier.padding(start = 8.dp))
            }
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
                    Text(stringResource(R.string.delete))
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
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            contentColor = MaterialTheme.colorScheme.primary,
            shape = MaterialTheme.shapes.medium,
        ) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Dns, contentDescription = null, modifier = Modifier.size(21.dp))
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!hideAddress) {
                Text(
                    profile.baseUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ConnectionPill(
                    label = stringResource(
                        if (profile.usesCleartext) R.string.http_connection else R.string.https_connection,
                    ),
                    secure = !profile.usesCleartext,
                )
                if (active) {
                    ConnectionPill(
                        label = stringResource(R.string.active_server),
                        secure = true,
                    )
                }
            }
        }
        if (!active) {
            IconButton(onClick = onSwitch) {
                Icon(
                    Icons.Outlined.SwapHoriz,
                    contentDescription = stringResource(R.string.switch_server),
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Outlined.DeleteOutline,
                contentDescription = stringResource(R.string.delete),
            )
        }
    }
}