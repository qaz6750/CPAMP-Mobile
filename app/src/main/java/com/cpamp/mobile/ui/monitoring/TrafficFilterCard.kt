package com.cpamp.mobile.ui.monitoring

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
    availableModels: List<String>,
    availableProviders: List<String>,
    onWindowSelected: (TrafficWindow) -> Unit,
    onFailedOnlyChanged: (Boolean) -> Unit,
    onModelsChanged: (List<String>) -> Unit,
    onProvidersChanged: (List<String>) -> Unit,
) {
    val advancedFilterCount = filter.models.size + filter.providers.size
    var advancedExpanded by rememberSaveable { mutableStateOf(advancedFilterCount > 0) }

    AppCard {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Outlined.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
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
                IconButton(
                    onClick = { advancedExpanded = !advancedExpanded },
                    modifier = Modifier.size(36.dp),
                ) {
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
                        modifier = Modifier.weight(1f).height(COMPACT_CONTROL_HEIGHT),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !filter.failedOnly,
                    onClick = { onFailedOnlyChanged(false) },
                    label = { Text(stringResource(R.string.all_requests)) },
                    modifier = Modifier.height(COMPACT_CONTROL_HEIGHT),
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
                    modifier = Modifier.height(COMPACT_CONTROL_HEIGHT),
                )
            }
            AnimatedVisibility(visible = advancedExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MultiSelectMenu(
                            selected = filter.models,
                            options = availableModels,
                            onSelectionChanged = onModelsChanged,
                            label = stringResource(R.string.filter_models),
                            allLabel = stringResource(R.string.all_models),
                            modifier = Modifier.weight(1f),
                        )
                        MultiSelectMenu(
                            selected = filter.providers,
                            options = availableProviders,
                            onSelectionChanged = onProvidersChanged,
                            label = stringResource(R.string.filter_providers),
                            allLabel = stringResource(R.string.all_providers),
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

@StringRes
private fun TrafficWindow.labelResource(): Int = when (this) {
    TrafficWindow.Hour -> R.string.last_hour
    TrafficWindow.Day -> R.string.last_24_hours
    TrafficWindow.Week -> R.string.last_7_days
}

@Composable
private fun MultiSelectMenu(
    selected: List<String>,
    options: List<String>,
    onSelectionChanged: (List<String>) -> Unit,
    label: String,
    allLabel: String,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val visibleOptions = (options + selected).distinct()
    Box(modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = visibleOptions.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(COMPACT_CONTROL_HEIGHT),
        ) {
            Text(
                if (selected.isEmpty()) label else stringResource(R.string.selected_filter_count, label, selected.size),
                maxLines = 1,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(allLabel) },
                leadingIcon = { Checkbox(checked = selected.isEmpty(), onCheckedChange = null) },
                onClick = {
                    onSelectionChanged(emptyList())
                    expanded = false
                },
            )
            visibleOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, maxLines = 1) },
                    leadingIcon = { Checkbox(checked = option in selected, onCheckedChange = null) },
                    onClick = {
                        onSelectionChanged(if (option in selected) selected - option else selected + option)
                    },
                )
            }
        }
    }
}

private val COMPACT_CONTROL_HEIGHT = 36.dp

