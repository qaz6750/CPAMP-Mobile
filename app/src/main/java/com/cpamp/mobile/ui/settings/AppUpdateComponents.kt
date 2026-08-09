package com.cpamp.mobile.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
        Text(updateStatusText(state), style = MaterialTheme.typography.bodySmall, color = updateStatusColor(state))
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
            UpdateStatus.Checking, UpdateStatus.Downloading, UpdateStatus.Verifying -> UpdateActionButton(
                label = updateStatusText(state),
                icon = { CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) },
                enabled = false,
                onClick = {},
            )
            else -> UpdateActionButton(
                label = stringResource(R.string.check_for_updates),
                icon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                onClick = onCheckForUpdates,
            )
        }
        TextButton(onClick = onShowUpdate, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.view_update_details))
            Icon(Icons.Outlined.ChevronRight, contentDescription = null)
        }
        TextButton(onClick = onOpenSourceLicenses, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.open_source_licenses))
        }
    }
}

@Composable
private fun UpdateActionButton(
    label: String,
    icon: @Composable () -> Unit,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
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
