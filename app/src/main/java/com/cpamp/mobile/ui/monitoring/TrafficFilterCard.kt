package com.cpamp.mobile.ui.monitoring

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
    val advancedFilterCount = listOf(
        filter.models.isNotBlank(),
        filter.providers.isNotBlank(),
        filter.minLatencyMs > 0,
    ).count { it }
    var advancedExpanded by rememberSaveable { mutableStateOf(advancedFilterCount > 0) }

    AppCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Outlined.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    stringResource(R.string.request_filters),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (advancedFilterCount > 0) {
                    Text(
                        stringResource(R.string.active_advanced_filter_count, advancedFilterCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = { advancedExpanded = !advancedExpanded }) {
                    Icon(
                        if (advancedExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = stringResource(
                            if (advancedExpanded) R.string.collapse_advanced_filters
                            else R.string.expand_advanced_filters,
                        ),
                    )
                }
            }
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
            AnimatedVisibility(visible = advancedExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    FilterTextField(
                        value = filter.models,
                        onValueChange = onModelsChanged,
                        label = stringResource(R.string.filter_models),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FilterTextField(
                        value = filter.providers,
                        onValueChange = onProvidersChanged,
                        label = stringResource(R.string.filter_providers),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.filter_min_latency),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
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
                                )
                            }
                        }
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
