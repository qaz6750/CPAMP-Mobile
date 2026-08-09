package com.cpamp.mobile.ui.accounts

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.cpamp.mobile.data.accounts.AccountHealth
import com.cpamp.mobile.data.accounts.AccountHealthFailure
import com.cpamp.mobile.data.accounts.AccountHealthSource
import com.cpamp.mobile.data.accounts.AccountQuotaState
import com.cpamp.mobile.data.accounts.AccountQuotaWindow
import com.cpamp.mobile.data.accounts.AccountStatus
import com.cpamp.mobile.ui.common.asDateTime
import com.cpamp.mobile.ui.common.safeServerName
import com.cpamp.mobile.ui.components.AppBackground
import com.cpamp.mobile.ui.components.AppCard
import com.cpamp.mobile.ui.components.ContentStateCard
import com.cpamp.mobile.ui.components.CredentialProviderIcon
import com.cpamp.mobile.ui.components.LoadingIconButton
import com.cpamp.mobile.ui.components.PageHeader

@Composable
fun AccountsScreen(
    contentPadding: PaddingValues,
    hideAddresses: Boolean,
    onOpenAccount: (String) -> Unit,
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val accounts = state.snapshot?.accounts.orEmpty()
    val active = accounts.filter { it.status == AccountStatus.Active }
    val disabled = accounts.filter { it.status == AccountStatus.Disabled }

    AppBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = contentPadding.calculateTopPadding() + 24.dp,
                bottom = contentPadding.calculateBottomPadding() + 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                val fallback = stringResource(R.string.nav_accounts)
                PageHeader(
                    eyebrow = state.profile?.let { profile ->
                        safeServerName(profile.name, profile.baseUrl, hideAddresses, fallback)
                    } ?: fallback,
                    title = stringResource(R.string.accounts_title),
                    subtitle = stringResource(R.string.accounts_subtitle),
                    trailing = {
                        LoadingIconButton(
                            icon = Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.accounts_refresh_quotas),
                            loading = state.refreshing,
                            enabled = !state.loading,
                            onClick = viewModel::refresh,
                        )
                    },
                )
            }
            state.snapshot?.let { snapshot ->
                item {
                    Text(
                        snapshot.inspectionRunId?.let { runId ->
                            stringResource(R.string.accounts_source_inspection, runId, snapshot.observedAtMs.asDateTime())
                        } ?: stringResource(R.string.accounts_source_inventory, snapshot.observedAtMs.asDateTime()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state.snapshot?.fromCache == true) {
                item { AccountsNotice(stringResource(R.string.accounts_cached), false) }
            }
            state.error?.let { error ->
                item { AccountsNotice(stringResource(error.messageResource()), true) }
            }
            when {
                state.loading && accounts.isEmpty() -> item {
                    ContentStateCard(stringResource(R.string.accounts_loading), loading = true)
                }
                accounts.isEmpty() -> item {
                    ContentStateCard(stringResource(R.string.accounts_empty))
                }
                else -> {
                    if (active.isNotEmpty()) {
                        item { AccountSectionTitle(R.string.credential_quota_active, active.size) }
                        items(active, key = AccountHealth::stableId) { account ->
                            AccountSummaryCard(account) { onOpenAccount(account.stableId) }
                        }
                    }
                    if (disabled.isNotEmpty()) {
                        item { AccountSectionTitle(R.string.credential_quota_disabled, disabled.size) }
                        items(disabled, key = AccountHealth::stableId) { account ->
                            AccountSummaryCard(account) { onOpenAccount(account.stableId) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AccountDetailScreen(
    contentPadding: PaddingValues,
    accountId: String,
    onBack: () -> Unit,
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    val detailFlow = remember(viewModel, accountId) { viewModel.detail(accountId) }
    val state by detailFlow.collectAsStateWithLifecycle(initialValue = AccountDetailUiState())

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
                    eyebrow = stringResource(R.string.nav_accounts),
                    title = state.account?.displayTitle() ?: stringResource(R.string.accounts_detail_title),
                    subtitle = stringResource(R.string.accounts_detail_subtitle),
                    leading = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    },
                )
            }
            if (state.fromCache) {
                item { AccountsNotice(stringResource(R.string.accounts_detail_cached), false) }
            }
            val account = state.account
            if (account == null) {
                item { ContentStateCard(stringResource(R.string.accounts_detail_unavailable), isError = true) }
            } else {
                item { AccountIdentityCard(account, state.observedAtMs) }
                item { AccountQuotaCard(account) }
            }
        }
    }
}

@Composable
private fun AccountsNotice(message: String, isError: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Text(message, modifier = Modifier.fillMaxWidth().padding(14.dp), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AccountSectionTitle(@StringRes label: Int, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(label), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AccountSummaryCard(account: AccountHealth, onClick: () -> Unit) {
    AppCard(
        modifier = Modifier.clickable(onClick = onClick),
        containerColor = if (account.status == AccountStatus.Disabled) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surface
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CredentialProviderIcon(account.provider)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    account.displayTitle(),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        providerLabel(account.provider).takeUnless { it == account.displayTitle() },
                        account.planType.trim().takeIf(String::isNotEmpty),
                    ).joinToString(" · ").ifBlank { stringResource(R.string.accounts_no_plan) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AccountStatusBadge(account)
                    account.windows.minOfOrNull { it.remainingPercent ?: 101.0 }
                        ?.takeIf { it <= 100.0 }
                        ?.let { remaining ->
                            Text(
                                stringResource(R.string.credential_quota_remaining, remaining),
                                style = MaterialTheme.typography.labelSmall,
                                color = quotaLevelColor(quotaLevel(remaining)),
                            )
                        }
                }
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = stringResource(R.string.accounts_open_details))
        }
    }
}

@Composable
private fun AccountIdentityCard(account: AccountHealth, observedAtMs: Long?) {
    AppCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CredentialProviderIcon(account.provider)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(account.displayTitle(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        providerLabel(account.provider),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AccountStatusBadge(account)
            }
            AccountDetailValue(R.string.accounts_provider, providerLabel(account.provider))
            AccountDetailValue(R.string.accounts_plan, account.planType.ifBlank { stringResource(R.string.accounts_no_plan) })
            AccountDetailValue(R.string.accounts_source, stringResource(account.source.labelResource()))
            observedAtMs?.let {
                AccountDetailValue(R.string.accounts_observed_at, it.asDateTime())
            }
        }
    }
}

@Composable
private fun AccountDetailValue(@StringRes label: Int, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            modifier = Modifier.padding(start = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AccountQuotaCard(account: AccountHealth) {
    AppCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.accounts_quota), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            when {
                account.status == AccountStatus.Disabled -> Text(
                    stringResource(R.string.credential_quota_disabled_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                account.quotaState == AccountQuotaState.Failed -> Text(
                    stringResource(account.failure.messageResource()),
                    color = MaterialTheme.colorScheme.error,
                )
                account.quotaState == AccountQuotaState.NotRequested -> Text(
                    stringResource(R.string.accounts_quota_not_refreshed),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                account.quotaState == AccountQuotaState.Unsupported -> Text(
                    stringResource(R.string.accounts_quota_unsupported),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                account.windows.isEmpty() -> Text(
                    stringResource(R.string.credential_quota_no_windows),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> account.windows.forEach { window -> AccountQuotaWindowRow(window) }
            }
        }
    }
}

@Composable
private fun AccountQuotaWindowRow(window: AccountQuotaWindow) {
    val remaining = window.remainingPercent?.coerceIn(0.0, 100.0)
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(window.durationLabel(), style = MaterialTheme.typography.labelLarge)
            Text(
                remaining?.let { stringResource(R.string.credential_quota_remaining, it) }
                    ?: stringResource(R.string.credential_quota_remaining_unknown),
                style = MaterialTheme.typography.labelMedium,
                color = quotaLevelColor(quotaLevel(remaining)),
            )
        }
        LinearProgressIndicator(
            progress = { ((remaining ?: 0.0) / 100.0).toFloat() },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = quotaLevelColor(quotaLevel(remaining)),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        window.resetAtMs?.let {
            Text(
                stringResource(R.string.credential_quota_reset, it.asDateTime()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        window.resetLabel.takeIf { it.isNotBlank() && it != "-" }?.let { label ->
            Text(
                stringResource(R.string.credential_quota_reset, label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun AccountStatusBadge(account: AccountHealth) {
    val (label, color) = when {
        account.status == AccountStatus.Disabled -> R.string.credential_quota_disabled_badge to MaterialTheme.colorScheme.onSurfaceVariant
        account.quotaState == AccountQuotaState.Failed -> R.string.credential_quota_failed_badge to MaterialTheme.colorScheme.error
        account.quotaState == AccountQuotaState.Available -> R.string.accounts_health_ready to MaterialTheme.colorScheme.tertiary
        account.quotaState == AccountQuotaState.NotRequested -> R.string.accounts_health_not_refreshed to MaterialTheme.colorScheme.primary
        else -> R.string.accounts_health_basic to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = color.copy(alpha = 0.12f), contentColor = color, shape = RoundedCornerShape(8.dp)) {
        Text(stringResource(label), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
    }
}

internal fun AccountHealth.displayTitle(): String = account.trim().ifBlank { name.trim() }.ifBlank { providerLabel(provider) }

internal fun providerLabel(provider: String): String = when (provider.trim().lowercase()) {
    "codex", "openai" -> "OpenAI Codex"
    "xai", "grok" -> "xAI"
    "claude", "anthropic" -> "Anthropic"
    "gemini", "gemini-cli", "aistudio", "vertex", "antigravity" -> "Google"
    "qwen" -> "Qwen"
    "deepseek" -> "DeepSeek"
    "kimi" -> "Kimi"
    else -> provider.trim().replaceFirstChar { it.titlecase() }.ifBlank { "AI" }
}

internal enum class QuotaLevel { Healthy, Warning, Critical, Unknown }

internal fun quotaLevel(remainingPercent: Double?): QuotaLevel = when {
    remainingPercent == null -> QuotaLevel.Unknown
    remainingPercent >= 50.0 -> QuotaLevel.Healthy
    remainingPercent >= 20.0 -> QuotaLevel.Warning
    else -> QuotaLevel.Critical
}

@Composable
internal fun quotaLevelColor(level: QuotaLevel) = when (level) {
    QuotaLevel.Healthy -> MaterialTheme.colorScheme.tertiary
    QuotaLevel.Warning -> QuotaOrange
    QuotaLevel.Critical -> MaterialTheme.colorScheme.error
    QuotaLevel.Unknown -> MaterialTheme.colorScheme.outline
}

@Composable
internal fun AccountQuotaWindow.durationLabel(): String = when {
    label.isNotBlank() -> label
    durationSeconds > 0 && durationSeconds % SECONDS_PER_DAY == 0L ->
        stringResource(R.string.credential_quota_window_days, durationSeconds / SECONDS_PER_DAY)
    durationSeconds > 0 && durationSeconds % SECONDS_PER_HOUR == 0L ->
        stringResource(R.string.credential_quota_window_hours, durationSeconds / SECONDS_PER_HOUR)
    else -> stringResource(R.string.credential_quota_window_other)
}

internal val QuotaOrange = androidx.compose.ui.graphics.Color(0xFFF59E0B)

@StringRes
private fun AccountsError.messageResource(): Int = when (this) {
    AccountsError.Unauthorized -> R.string.accounts_unauthorized
    AccountsError.RateLimited -> R.string.accounts_rate_limited
    AccountsError.Network -> R.string.accounts_network_error
    AccountsError.Server -> R.string.accounts_unavailable
}

@StringRes
private fun AccountHealthSource.labelResource(): Int = when (this) {
    AccountHealthSource.AuthFile -> R.string.accounts_source_auth_file
    AccountHealthSource.Inspection -> R.string.accounts_source_server_inspection
    AccountHealthSource.Direct -> R.string.accounts_source_provider
    AccountHealthSource.Cache -> R.string.accounts_source_cache
}

@StringRes
private fun AccountHealthFailure?.messageResource(): Int = when (this) {
    AccountHealthFailure.Inspection -> R.string.credential_quota_server_result_error
    AccountHealthFailure.ProviderRequest -> R.string.credential_quota_provider_request_error
    null -> R.string.credential_quota_temporarily_unavailable
}
