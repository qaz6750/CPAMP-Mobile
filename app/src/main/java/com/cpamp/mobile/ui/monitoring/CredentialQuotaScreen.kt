package com.cpamp.mobile.ui.monitoring

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cpamp.mobile.R
import com.cpamp.mobile.common.SECONDS_PER_DAY
import com.cpamp.mobile.common.SECONDS_PER_HOUR
import com.cpamp.mobile.data.monitoring.CredentialAccountStatus
import com.cpamp.mobile.data.monitoring.CredentialQuota
import com.cpamp.mobile.data.monitoring.CredentialQuotaQueryState
import com.cpamp.mobile.data.monitoring.CredentialQuotaWindow
import com.cpamp.mobile.ui.common.asDateTime
import com.cpamp.mobile.ui.components.AppBackground
import com.cpamp.mobile.ui.components.AppCard
import com.cpamp.mobile.ui.components.ContentStateCard
import com.cpamp.mobile.ui.components.CredentialProviderIcon
import com.cpamp.mobile.ui.components.LoadingIconButton
import com.cpamp.mobile.ui.components.PageHeader

@Composable
fun CredentialQuotaScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    viewModel: MonitoringViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val quotas = state.credentialQuotas
    val active = quotas.filter { it.accountStatus == CredentialAccountStatus.Active }
    val disabled = quotas.filter { it.accountStatus == CredentialAccountStatus.Disabled }

    AppBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = contentPadding.calculateTopPadding() + 24.dp,
                bottom = contentPadding.calculateBottomPadding() + 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                PageHeader(
                    eyebrow = stringResource(R.string.nav_traffic),
                    title = stringResource(R.string.credential_quota_details),
                    subtitle = stringResource(R.string.credential_quota_summary),
                    leading = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    },
                    trailing = {
                        LoadingIconButton(
                            icon = Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.refresh),
                            loading = state.credentialQuotasLoading,
                            onClick = viewModel::refreshCredentialQuotas,
                        )
                    },
                )
            }
            when {
                state.credentialQuotasLoading && quotas.isEmpty() -> item {
                    ContentStateCard(
                        message = stringResource(R.string.credential_quota_loading),
                        loading = true,
                    )
                }
                state.credentialQuotasError -> item {
                    ContentStateCard(
                        message = stringResource(R.string.credential_quota_unavailable),
                        isError = true,
                    )
                }
                quotas.isEmpty() -> item {
                    ContentStateCard(message = stringResource(R.string.credential_quota_empty))
                }
                else -> {
                    if (active.isNotEmpty()) {
                        item { CredentialQuotaSectionTitle(R.string.credential_quota_active, active.size) }
                        items(active, key = { "${it.provider}:${it.name}" }) { CredentialQuotaDetailCard(it) }
                    }
                    if (disabled.isNotEmpty()) {
                        item { CredentialQuotaSectionTitle(R.string.credential_quota_disabled, disabled.size) }
                        items(disabled, key = { "${it.provider}:${it.name}" }) { CredentialQuotaDetailCard(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CredentialQuotaSectionTitle(@StringRes label: Int, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CredentialQuotaDetailCard(quota: CredentialQuota) {
    val disabled = quota.accountStatus == CredentialAccountStatus.Disabled
    AppCard(
        containerColor = if (disabled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CredentialProviderIcon(quota.provider)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        val providerLabel = quota.provider.providerLabel()
                        val plan = quota.displayPlan(providerLabel)
                        Text(
                            quota.account.ifBlank { quota.name },
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (disabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            listOfNotNull(providerLabel, plan).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                quota.statusLabel()?.let { status ->
                    Surface(
                        color = when {
                            disabled -> MaterialTheme.colorScheme.surfaceVariant
                            quota.queryState == CredentialQuotaQueryState.Failed -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.secondaryContainer
                        },
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            status,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                quota.queryState == CredentialQuotaQueryState.Failed -> MaterialTheme.colorScheme.onErrorContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
            when {
                disabled -> Text(
                    stringResource(R.string.credential_quota_disabled_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                quota.queryState == CredentialQuotaQueryState.Unsupported -> Text(
                    stringResource(R.string.credential_quota_unsupported_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                quota.queryState == CredentialQuotaQueryState.Failed && quota.windows.isEmpty() -> Text(
                    stringResource(quota.failureMessage()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                quota.windows.isEmpty() -> Text(
                    stringResource(R.string.credential_quota_no_windows),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> {
                    if (quota.stale) {
                        Text(
                            stringResource(R.string.credential_quota_stale_item),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    quota.windows.forEach { CredentialQuotaDetailWindow(it) }
                }
            }
        }
    }
}

@Composable
private fun CredentialQuotaDetailWindow(window: CredentialQuotaWindow) {
    val remaining = window.remainingPercent?.coerceIn(0.0, 100.0)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(window.detailDurationLabel(), style = MaterialTheme.typography.labelLarge)
            Text(
                remaining?.let { stringResource(R.string.credential_quota_remaining, it) }
                    ?: stringResource(R.string.credential_quota_remaining_unknown),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { ((remaining ?: 0.0) / 100.0).toFloat() },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = when (credentialQuotaLevel(remaining)) {
                CredentialQuotaLevel.Unknown -> MaterialTheme.colorScheme.outlineVariant
                CredentialQuotaLevel.Healthy -> MaterialTheme.colorScheme.tertiary
                CredentialQuotaLevel.Warning -> QuotaOrange
                CredentialQuotaLevel.Critical -> MaterialTheme.colorScheme.error
            },
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        window.resetAtMs?.let {
            Text(
                stringResource(R.string.credential_quota_reset, it.asDateTime()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CredentialQuota.statusLabel(): String? = when {
    accountStatus == CredentialAccountStatus.Disabled -> stringResource(R.string.credential_quota_disabled_badge)
    queryState == CredentialQuotaQueryState.Unsupported -> stringResource(R.string.credential_quota_unsupported_badge)
    queryState == CredentialQuotaQueryState.Failed -> stringResource(R.string.credential_quota_failed_badge)
    else -> null
}

private fun String.providerLabel(): String = when (this) {
    "codex" -> "OpenAI Codex"
    "xai" -> "xAI"
    "claude", "anthropic" -> "Anthropic"
    "gemini", "gemini-cli", "aistudio", "vertex" -> "Google"
    "qwen" -> "Qwen"
    "deepseek" -> "DeepSeek"
    "unknown" -> "AI"
    else -> replaceFirstChar { character -> character.titlecase() }
}

private fun CredentialQuota.displayPlan(providerLabel: String): String? = planType.trim().takeIf { plan ->
    plan.isNotEmpty() &&
        plan.lowercase() !in setOf("unknown", "none", "null", "codex", "openai", "chatgpt", "xai", "grok") &&
        !providerLabel.equals(plan, ignoreCase = true)
}

@StringRes
private fun CredentialQuota.failureMessage(): Int = when (failure) {
    com.cpamp.mobile.data.monitoring.CredentialQuotaFailure.MissingAuthIndex -> R.string.credential_quota_missing_auth
    com.cpamp.mobile.data.monitoring.CredentialQuotaFailure.RateLimited -> R.string.credential_quota_rate_limited
    com.cpamp.mobile.data.monitoring.CredentialQuotaFailure.ProviderUnavailable -> R.string.credential_quota_provider_unavailable
    com.cpamp.mobile.data.monitoring.CredentialQuotaFailure.InvalidResponse -> R.string.credential_quota_invalid_response
    else -> R.string.credential_quota_item_error
}

private val QuotaOrange = androidx.compose.ui.graphics.Color(0xFFF59E0B)

internal enum class CredentialQuotaLevel { Healthy, Warning, Critical, Unknown }

internal fun credentialQuotaLevel(remainingPercent: Double?): CredentialQuotaLevel = when {
    remainingPercent == null -> CredentialQuotaLevel.Unknown
    remainingPercent >= 50.0 -> CredentialQuotaLevel.Healthy
    remainingPercent >= 20.0 -> CredentialQuotaLevel.Warning
    else -> CredentialQuotaLevel.Critical
}

@Composable
private fun CredentialQuotaWindow.detailDurationLabel(): String = when {
    durationSeconds > 0 && durationSeconds % SECONDS_PER_DAY == 0L ->
        stringResource(R.string.credential_quota_window_days, durationSeconds / SECONDS_PER_DAY)
    durationSeconds > 0 && durationSeconds % SECONDS_PER_HOUR == 0L ->
        stringResource(R.string.credential_quota_window_hours, durationSeconds / SECONDS_PER_HOUR)
    else -> stringResource(R.string.credential_quota_window_other)
}
