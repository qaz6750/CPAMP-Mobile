package com.cpamp.mobile.ui.monitoring

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cpamp.mobile.R

@Composable
internal fun TrafficFilterCard(
    filter: TrafficFilter,
    onWindowSelected: (TrafficWindow) -> Unit,
    onFailedOnlyChanged: (Boolean) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TrafficWindow.entries.forEach { window ->
                    FilterChip(
                        selected = filter.window == window,
                        onClick = { onWindowSelected(window) },
                        label = { Text(stringResource(window.labelResource())) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !filter.failedOnly,
                    onClick = { onFailedOnlyChanged(false) },
                    label = { Text(stringResource(R.string.all_requests)) },
                )
                FilterChip(
                    selected = filter.failedOnly,
                    onClick = { onFailedOnlyChanged(true) },
                    label = { Text(stringResource(R.string.failed_only)) },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        labelColor = MaterialTheme.colorScheme.error,
                        iconColor = MaterialTheme.colorScheme.error,
                        selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = filter.failedOnly,
                        borderColor = MaterialTheme.colorScheme.error,
                        selectedBorderColor = MaterialTheme.colorScheme.error,
                    ),
                )
                Text(
                    stringResource(R.string.refresh_after_filter),
                    modifier = Modifier.align(Alignment.CenterVertically),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@StringRes
private fun TrafficWindow.labelResource(): Int = when (this) {
    TrafficWindow.Hour -> R.string.last_hour
    TrafficWindow.Day -> R.string.last_24_hours
    TrafficWindow.Week -> R.string.last_7_days
}
