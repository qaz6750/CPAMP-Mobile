package com.cpamp.mobile.ui.accounts

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
    val providers = accounts.map { it.provider.normalizedProvider() }.distinct().sorted()
    var selectedProvider by rememberSaveable { mutableStateOf<String?>(null) }
    val effectiveProvider = selectedProvider?.takeIf(providers::contains)
    val visibleAccounts = accounts.filter { effectiveProvider == null || it.provider.normalizedProvider() == effectiveProvider }

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
            if (accounts.isNotEmpty()) {
                item {
                    AccountProviderFilters(
                        providers = providers,
                        selectedProvider = effectiveProvider,
                        accountCounts = accounts.groupingBy { it.provider.normalizedProvider() }.eachCount(),
                        totalCount = accounts.size,
                        onProviderSelected = { selectedProvider = it },
                    )
                }
                item { AccountHealthOverview(accounts) }
            }
            when {
                state.loading && accounts.isEmpty() -> item {
                    ContentStateCard(stringResource(R.string.accounts_loading), loading = true)
                }
                accounts.isEmpty() -> item {
                    ContentStateCard(stringResource(R.string.accounts_empty))
                }
                visibleAccounts.isEmpty() -> item {
                    ContentStateCard(stringResource(R.string.accounts_filter_empty))
                }
                else -> {
                    item { AccountSectionTitle(R.string.accounts_credentials, visibleAccounts.size) }
                    items(visibleAccounts, key = AccountHealth::stableId) { account ->
                        AccountSummaryCard(account) { onOpenAccount(account.stableId) }
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
private fun AccountProviderFilters(
    providers: List<String>,
    selectedProvider: String?,
    accountCounts: Map<String, Int>,
    totalCount: Int,
    onProviderSelected: (String?) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    ) {
        Surface(
            shape = AccountCardShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AccountProviderTab(
                    label = stringResource(R.string.accounts_filter_all),
                    count = totalCount,
                    selected = selectedProvider == null,
                    onClick = { onProviderSelected(null) },
                )
                providers.forEach { provider ->
                    VerticalDivider(
                        modifier = Modifier.height(18.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    AccountProviderTab(
                        label = stringResource(R.string.accounts_filter_provider, providerLabel(provider)),
                        count = accountCounts[provider] ?: 0,
                        selected = selectedProvider == provider,
                        onClick = { onProviderSelected(provider) },
                        leadingIcon = {
                            CredentialProviderIcon(provider, modifier = Modifier.size(18.dp))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountProviderTab(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .heightIn(min = 36.dp)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leadingIcon?.invoke()
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Surface(
                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
                contentColor = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                shape = RoundedCornerShape(50),
            ) {
                Text(
                    count.toString(),
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun AccountCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit,
) {
    AppCard(
        modifier = modifier,
        containerColor = containerColor,
        shape = AccountCardShape,
        content = content,
    )
}

@Composable
private fun AccountPanel(content: @Composable () -> Unit) {
    AppCard(
        shape = AccountPanelShape,
        content = content,
    )
}

@Composable
private fun AccountHealthOverview(accounts: List<AccountHealth>) {
    val healthy = accounts.count {
        it.status == AccountStatus.Active && it.quotaState == AccountQuotaState.Available &&
            quotaLevel(it.minimumRemainingPercent()) == QuotaLevel.Healthy
    }
    val needsAttention = accounts.count {
        it.status == AccountStatus.Active && it.quotaState == AccountQuotaState.Failed
    }
    val quotaRisk = accounts.count {
        it.status == AccountStatus.Active && it.quotaState == AccountQuotaState.Available &&
            quotaLevel(it.minimumRemainingPercent()) in setOf(QuotaLevel.Warning, QuotaLevel.Critical)
    }
    val disabled = accounts.count { it.status == AccountStatus.Disabled }
    val pending = accounts.count {
        it.status == AccountStatus.Active && it.quotaState in setOf(
            AccountQuotaState.NotRequested,
            AccountQuotaState.Unsupported,
        )
    }
    val metrics = listOf(
        AccountHealthMetric(R.string.accounts_overview_total, accounts.size, Icons.Outlined.AccountCircle, MaterialTheme.colorScheme.primary),
        AccountHealthMetric(R.string.accounts_overview_healthy, healthy, Icons.Outlined.CheckCircle, MaterialTheme.colorScheme.tertiary),
        AccountHealthMetric(R.string.accounts_overview_attention, needsAttention, Icons.Outlined.ErrorOutline, MaterialTheme.colorScheme.error),
        AccountHealthMetric(R.string.accounts_overview_risk, quotaRisk, Icons.Outlined.Speed, QuotaOrange),
        AccountHealthMetric(R.string.accounts_overview_disabled, disabled, Icons.Outlined.Block, MaterialTheme.colorScheme.onSurfaceVariant),
        AccountHealthMetric(R.string.accounts_overview_pending, pending, Icons.Outlined.HelpOutline, MaterialTheme.colorScheme.secondary),
    )
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        metrics.forEach { metric ->
            AccountHealthMetricCard(metric, Modifier.width(136.dp))
        }
    }
}

@Composable
private fun AccountHealthMetricCard(metric: AccountHealthMetric, modifier: Modifier = Modifier) {
    AccountCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = metric.color.copy(alpha = 0.12f),
                contentColor = metric.color,
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(metric.icon, contentDescription = null, modifier = Modifier.padding(8.dp).height(20.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(metric.count.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(metric.label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
}

private data class AccountHealthMetric(
    @StringRes val label: Int,
    val count: Int,
    val icon: ImageVector,
    val color: Color,
)

@Composable
private fun AccountsNotice(message: String, isError: Boolean) {
    AccountCard(
        containerColor = if (isError) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.secondaryContainer,
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
    val remaining = account.minimumRemainingPercent()
    val level = quotaLevel(remaining)
    AccountCard(
        modifier = Modifier.clip(AccountCardShape).clickable(onClick = onClick),
        containerColor = if (account.status == AccountStatus.Disabled) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)
        } else {
            MaterialTheme.colorScheme.surface
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AccountProviderBadge(account.provider)
                    account.planType.trim().takeIf(String::isNotEmpty)?.let { plan ->
                        AccountPlanBadge(plan)
                    }
                }
                AccountStatusBadge(account)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        account.displayTitle(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        account.name.trim().ifBlank { stringResource(R.string.accounts_credential_unknown) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = stringResource(R.string.accounts_open_details),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (remaining != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.accounts_quota),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.credential_quota_remaining, remaining),
                        style = MaterialTheme.typography.labelMedium,
                        color = quotaLevelColor(level),
                        fontWeight = FontWeight.Bold,
                    )
                }
                LinearProgressIndicator(
                    progress = { (remaining / 100.0).toFloat() },
                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(50)),
                    color = quotaLevelColor(level),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AccountProviderBadge(provider: String) {
    val accent = providerAccentColor(provider)
    Surface(
        color = accent.copy(alpha = 0.10f),
        contentColor = accent,
        shape = RoundedCornerShape(9.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CredentialProviderIcon(provider, modifier = Modifier.size(16.dp))
            Text(
                providerBadgeLabel(provider),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun AccountPlanBadge(plan: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            plan,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AccountIdentityCard(account: AccountHealth, observedAtMs: Long?) {
    AccountPanel {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AccountProviderBadge(account.provider)
                account.planType.trim().takeIf(String::isNotEmpty)?.let { plan ->
                    AccountPlanBadge(plan)
                }
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                AccountStatusBadge(account)
            }
            Text(account.displayTitle(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
    AccountPanel {
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
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(window.durationLabel(), style = MaterialTheme.typography.labelLarge)
                Text(
                    remaining?.let { stringResource(R.string.credential_quota_remaining, it) }
                        ?: stringResource(R.string.credential_quota_remaining_unknown),
                    style = MaterialTheme.typography.labelMedium,
                    color = quotaLevelColor(quotaLevel(remaining)),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            LinearProgressIndicator(
                progress = { ((remaining ?: 0.0) / 100.0).toFloat() },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = quotaLevelColor(quotaLevel(remaining)),
                trackColor = MaterialTheme.colorScheme.surface,
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
}

private fun AccountHealth.minimumRemainingPercent(): Double? =
    windows.mapNotNull(AccountQuotaWindow::remainingPercent).minOrNull()?.coerceIn(0.0, 100.0)

private fun String.normalizedProvider(): String = trim().lowercase()

@Composable
internal fun AccountStatusBadge(account: AccountHealth) {
    val (label, color) = when {
        account.status == AccountStatus.Disabled -> R.string.credential_quota_disabled_badge to MaterialTheme.colorScheme.onSurfaceVariant
        account.quotaState == AccountQuotaState.Failed -> R.string.credential_quota_failed_badge to MaterialTheme.colorScheme.error
        account.quotaState == AccountQuotaState.Available &&
            quotaLevel(account.minimumRemainingPercent()) == QuotaLevel.Critical ->
            R.string.accounts_health_quota_critical to MaterialTheme.colorScheme.error
        account.quotaState == AccountQuotaState.Available &&
            quotaLevel(account.minimumRemainingPercent()) == QuotaLevel.Warning ->
            R.string.accounts_health_quota_warning to QuotaOrange
        account.quotaState == AccountQuotaState.Available -> R.string.accounts_health_ready to MaterialTheme.colorScheme.tertiary
        account.quotaState == AccountQuotaState.NotRequested -> R.string.accounts_health_not_refreshed to MaterialTheme.colorScheme.primary
        else -> R.string.accounts_health_basic to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = color.copy(alpha = 0.12f),
        contentColor = color,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, color.copy(alpha = 0.24f)),
    ) {
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

internal fun providerBadgeLabel(provider: String): String = when (provider.trim().lowercase()) {
    "codex", "openai" -> "Codex"
    "xai", "grok" -> "xAI"
    "claude", "anthropic" -> "Claude"
    "gemini", "gemini-cli", "aistudio", "vertex", "antigravity" -> "Gemini"
    else -> providerLabel(provider)
}

@Composable
private fun providerAccentColor(provider: String): Color = when (provider.trim().lowercase()) {
    "claude", "anthropic" -> Color(0xFFD97757)
    "qwen" -> Color(0xFF8B5CF6)
    "xai", "grok" -> MaterialTheme.colorScheme.onSurface
    "kimi" -> Color(0xFF027AFF)
    "gemini", "gemini-cli", "aistudio", "vertex", "antigravity" -> Color(0xFF3186FF)
    else -> MaterialTheme.colorScheme.primary
}

internal enum class QuotaLevel { Healthy, Warning, Critical, Unknown }

internal fun quotaLevel(remainingPercent: Double?): QuotaLevel = when {
    remainingPercent == null -> QuotaLevel.Unknown
    remainingPercent >= 70.0 -> QuotaLevel.Healthy
    remainingPercent >= 30.0 -> QuotaLevel.Warning
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

private val AccountCardShape = RoundedCornerShape(12.dp)
private val AccountPanelShape = RoundedCornerShape(14.dp)

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
