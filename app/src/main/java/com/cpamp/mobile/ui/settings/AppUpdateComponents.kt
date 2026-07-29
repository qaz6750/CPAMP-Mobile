package com.cpamp.mobile.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cpamp.mobile.BuildConfig
import com.cpamp.mobile.R
import com.cpamp.mobile.data.update.AppUpdateState
import com.cpamp.mobile.data.update.UpdateError
import com.cpamp.mobile.data.update.UpdateStatus
import com.cpamp.mobile.data.update.displayBody

@Composable
internal fun UpdateSettingsCard(
    state: AppUpdateState,
    onCheck: () -> Unit,
    onShowUpdate: () -> Unit,
    onOpenSourceLicenses: () -> Unit,
) {
    val context = LocalContext.current
    SettingsCard(stringResource(R.string.about_updates)) {
        Text(
            stringResource(R.string.current_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodyMedium,
        )
        state.release?.let { release ->
            Text(
                stringResource(R.string.latest_version, release.tagName.removePrefix("v")),
                fontWeight = FontWeight.SemiBold,
            )
            release.publishedAt?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(updateStatusText(state), style = MaterialTheme.typography.bodySmall, color = statusColor(state))
        when (state.status) {
            UpdateStatus.Available, UpdateStatus.Downloading, UpdateStatus.Verifying -> Button(
                onClick = onShowUpdate,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Download, contentDescription = null)
                Text(stringResource(R.string.view_update_details), modifier = Modifier.padding(start = 8.dp))
            }
            UpdateStatus.ReadyToInstall -> Button(
                onClick = { state.installUri?.let { context.installUpdate(it) } },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.InstallMobile, contentDescription = null)
                Text(stringResource(R.string.install_update), modifier = Modifier.padding(start = 8.dp))
            }
            else -> Button(
                onClick = onCheck,
                enabled = state.status !in setOf(
                    UpdateStatus.Checking,
                    UpdateStatus.Downloading,
                    UpdateStatus.Verifying,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.status == UpdateStatus.Checking) {
                    CircularProgressIndicator(Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                }
                Text(stringResource(R.string.check_for_updates), modifier = Modifier.padding(start = 8.dp))
            }
        }
        TextButton(onClick = onOpenSourceLicenses, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.open_source_licenses))
        }
    }
}

@Composable
internal fun UpdateDetailsDialog(
    state: AppUpdateState,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
) {
    val context = LocalContext.current
    val language = LocalConfiguration.current.locales[0].language
    val release = state.release ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.update_details_title, release.tagName.removePrefix("v")))
                release.publishedAt?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                release.displayBody(language)?.let { body ->
                    Text(
                        body,
                        modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (state.status in setOf(UpdateStatus.Downloading, UpdateStatus.Verifying)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(updateStatusText(state), style = MaterialTheme.typography.bodySmall)
                        state.progressPercent?.let { progress ->
                            LinearProgressIndicator(
                                progress = { progress / 100f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } ?: LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {
            when (state.status) {
                UpdateStatus.Available -> Button(onClick = onDownload) {
                    Icon(Icons.Outlined.Download, contentDescription = null)
                    Text(stringResource(R.string.download_update), modifier = Modifier.padding(start = 8.dp))
                }
                UpdateStatus.ReadyToInstall -> Button(
                    onClick = { state.installUri?.let(context::installUpdate) },
                ) {
                    Icon(Icons.Outlined.InstallMobile, contentDescription = null)
                    Text(stringResource(R.string.install_update), modifier = Modifier.padding(start = 8.dp))
                }
                else -> Unit
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun updateStatusText(state: AppUpdateState): String = when (state.status) {
    UpdateStatus.Idle -> stringResource(R.string.update_manual_check)
    UpdateStatus.Checking -> stringResource(R.string.update_checking)
    UpdateStatus.NoRelease -> stringResource(R.string.update_no_release)
    UpdateStatus.UpToDate -> stringResource(R.string.update_up_to_date)
    UpdateStatus.Available -> stringResource(R.string.update_available)
    UpdateStatus.Downloading -> state.progressPercent?.let {
        stringResource(R.string.update_downloading_percent, it)
    } ?: stringResource(R.string.update_downloading)
    UpdateStatus.Verifying -> stringResource(R.string.update_verifying)
    UpdateStatus.ReadyToInstall -> stringResource(R.string.update_ready)
    UpdateStatus.Failed -> stringResource(
        when (state.error) {
            UpdateError.RateLimited -> R.string.update_rate_limited
            UpdateError.InvalidRelease -> R.string.update_invalid_release
            UpdateError.Checksum -> R.string.update_checksum_failed
            UpdateError.Signature -> R.string.update_signature_failed
            else -> R.string.update_failed
        },
    )
}

@Composable
private fun statusColor(state: AppUpdateState) = when (state.status) {
    UpdateStatus.Failed -> MaterialTheme.colorScheme.error
    UpdateStatus.Available, UpdateStatus.ReadyToInstall -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
