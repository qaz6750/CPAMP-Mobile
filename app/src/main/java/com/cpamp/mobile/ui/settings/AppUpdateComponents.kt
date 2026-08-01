package com.cpamp.mobile.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    onShowUpdate: () -> Unit,
    onOpenSourceLicenses: () -> Unit,
) {
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
        Button(
            onClick = onShowUpdate,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.view_update_details))
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        TextButton(onClick = onOpenSourceLicenses, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.open_source_licenses))
        }
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
