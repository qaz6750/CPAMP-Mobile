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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cpamp.mobile.R
import com.cpamp.mobile.ui.components.AppCard

@Composable
internal fun TrafficFilterCard(
    filter: TrafficFilter,
    onWindowSelected: (TrafficWindow) -> Unit,
    onFailedOnlyChanged: (Boolean) -> Unit,
    onModelsChanged: (String) -> Unit,
    onProvidersChanged: (String) -> Unit,
    onMinLatencyChanged: (Long) -> Unit,
) {
    AppCard {
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
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterTextField(
                    value = filter.models,
                    onValueChange = onModelsChanged,
                    label = stringResource(R.string.filter_models),
                    modifier = Modifier.weight(1f),
                )
                FilterTextField(
                    value = filter.providers,
                    onValueChange = onProvidersChanged,
                    label = stringResource(R.string.filter_providers),
                    modifier = Modifier.weight(1f),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.filter_min_latency),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MIN_LATENCY_OPTIONS.forEach { latencyMs ->
                        FilterChip(
                            selected = filter.minLatencyMs == latencyMs,
                            onClick = { onMinLatencyChanged(latencyMs) },
                            label = {
                                Text(
                                    if (latencyMs == 0L) stringResource(R.string.filter_any_latency)
                                    else stringResource(R.string.filter_latency_ms, latencyMs),
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.filters_refresh_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FilterTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, maxLines = 1) },
        placeholder = { Text(stringResource(R.string.filter_comma_separated), maxLines = 1) },
        singleLine = true,
        modifier = modifier,
    )
}

@StringRes
private fun TrafficWindow.labelResource(): Int = when (this) {
    TrafficWindow.Hour -> R.string.last_hour
    TrafficWindow.Day -> R.string.last_24_hours
    TrafficWindow.Week -> R.string.last_7_days
}

private val MIN_LATENCY_OPTIONS = listOf(0L, 500L, 1_000L, 2_000L)
