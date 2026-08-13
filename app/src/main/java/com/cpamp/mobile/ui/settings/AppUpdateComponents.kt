package com.cpamp.mobile.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Refresh
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cpamp.mobile.BuildConfig
import com.cpamp.mobile.R
import com.cpamp.mobile.data.update.AppUpdateState
import com.cpamp.mobile.data.update.UpdateError
import com.cpamp.mobile.data.update.UpdateStatus

@Composable
internal fun UpdateSettingsCard(
    state: AppUpdateState,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onShowUpdate: () -> Unit,
    onOpenSourceLicenses: () -> Unit,
) {
    val context = LocalContext.current
    SettingsGroupCard(
        title = stringResource(R.string.about_updates),
        subtitle = stringResource(R.string.current_version, BuildConfig.VERSION_NAME),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.release?.let { release ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.latest_version, release.tagName.removePrefix("v")),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    release.publishedAt?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            UpdateStatusSummary(state)
            when (state.status) {
                UpdateStatus.Available -> UpdateActionButton(
                    label = stringResource(R.string.download_update),
                    icon = { Icon(Icons.Outlined.Download, contentDescription = null) },
                    onClick = onDownloadUpdate,
                )
                UpdateStatus.ReadyToInstall -> UpdateActionButton(
                    label = stringResource(R.string.install_update),
                    icon = { Icon(Icons.Outlined.InstallMobile, contentDescription = null) },
                    onClick = { state.installUri?.let(context::installUpdate) },
                )
                UpdateStatus.Checking, UpdateStatus.Downloading, UpdateStatus.Verifying -> Unit
                else -> UpdateActionButton(
                    label = stringResource(R.string.check_for_updates),
                    icon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                    onClick = onCheckForUpdates,
                )
            }
        }
        SettingsDivider()
        UpdateLinkButton(
            label = stringResource(R.string.view_update_details),
            onClick = onShowUpdate,
        )
        SettingsDivider()
        UpdateLinkButton(
            label = stringResource(R.string.open_source_licenses),
            onClick = onOpenSourceLicenses,
        )
    }
}

@Composable
private fun UpdateStatusSummary(state: AppUpdateState) {
    val busy = state.status in setOf(
        UpdateStatus.Checking,
        UpdateStatus.Downloading,
        UpdateStatus.Verifying,
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .background(updateStatusColor(state), CircleShape),
                )
            }
            Text(
                updateStatusText(state),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = updateStatusColor(state),
            )
        }
        if (state.status in setOf(UpdateStatus.Downloading, UpdateStatus.Verifying)) {
            state.progressPercent?.let { progress ->
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            } ?: LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun UpdateLinkButton(
    label: String,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Icon(Icons.Outlined.ChevronRight, contentDescription = null)
    }
}

@Composable
private fun UpdateActionButton(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        icon()
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
internal fun updateStatusText(state: AppUpdateState): String = when (state.status) {
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
internal fun updateStatusColor(state: AppUpdateState) = when (state.status) {
    UpdateStatus.Failed -> MaterialTheme.colorScheme.error
    UpdateStatus.Available, UpdateStatus.ReadyToInstall -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
