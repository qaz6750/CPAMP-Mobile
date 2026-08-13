package com.cpamp.mobile.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.cpamp.mobile.ui.components.AppCard
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

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.system_servers),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.saved_servers_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        profiles.forEach { profile ->
            ServerSettingsCard(
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
private fun ServerSettingsCard(
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
    AppCard(
        containerColor = if (active) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
        },
        border = if (active) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(
                        Icons.Outlined.Dns,
                        contentDescription = null,
                        modifier = Modifier.padding(9.dp).size(20.dp),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
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
                }
                if (active) {
                    ConnectionPill(
                        label = stringResource(R.string.active_server),
                        secure = true,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ConnectionPill(
                    label = stringResource(
                        if (profile.usesCleartext) R.string.http_connection else R.string.https_connection,
                    ),
                    secure = !profile.usesCleartext,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!active) {
                        TextButton(onClick = onSwitch) {
                            Icon(Icons.Outlined.SwapHoriz, contentDescription = null)
                            Text(
                                stringResource(R.string.switch_server),
                                modifier = Modifier.padding(start = 6.dp),
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
        }
    }
}