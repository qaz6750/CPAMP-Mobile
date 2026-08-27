package com.cpamp.mobile.ui.accounts

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
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
import com.cpamp.mobile.data.accounts.AccountUsageState
import com.cpamp.mobile.data.accounts.estimatedQuotaCycleCost
import com.cpamp.mobile.ui.common.asDateTime
import com.cpamp.mobile.ui.common.asCost
import com.cpamp.mobile.ui.common.asPercent
import com.cpamp.mobile.ui.common.compactNumber
import com.cpamp.mobile.ui.common.compactTokens
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
    val error = state.error
    val notice = when {
        error != null && state.snapshot?.fromCache == true -> stringResource(
            R.string.accounts_error_cached,
            stringResource(error.messageResource()),
        ) to true
        error != null -> stringResource(error.messageResource()) to true
        state.snapshot?.fromCache == true -> stringResource(R.string.accounts_cached) to false
        accounts.isNotEmpty() && accounts.none { it.usageState == AccountUsageState.Available } -> {
            stringResource(R.string.accounts_usage_unavailable) to false
        }
        else -> null
    }

    AppBackground {
        LazyColumn(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxHeight()
                .widthIn(max = 1000.dp).fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = contentPadding.calculateTopPadding() + 20.dp,
                bottom = contentPadding.calculateBottomPadding() + 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                val fallback = stringResource(R.string.nav_accounts)
                val source = state.snapshot?.let { snapshot ->
                    stringResource(R.string.accounts_source_inventory, snapshot.observedAtMs.asDateTime())
                }
                PageHeader(
                    eyebrow = state.profile?.let { profile ->
                        safeServerName(profile.name, profile.baseUrl, hideAddresses, fallback)
                    } ?: fallback,
                    title = stringResource(R.string.accounts_title),
                    subtitle = source,
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
            notice?.let { (message, isError) ->
                item { AccountsNotice(message, isError) }
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
                    item { AccountSectionTitle(R.string.accounts_credentials) }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            visibleAccounts.forEach { account ->
                                AccountSummaryCard(account) { onOpenAccount(account.stableId) }
                            }
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
    val state by detailFlow.collectAsStateWithLifecycle(
        initialValue = AccountDetailUiState(loading = true),
    )
    val account = state.account

    AppBackground {
        LazyColumn(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxHeight()
                .widthIn(max = 900.dp).fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = contentPadding.calculateTopPadding() + 18.dp,
                bottom = contentPadding.calculateBottomPadding() + 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AccountDetailHeader(
                    onBack = onBack,
                    refreshing = state.refreshing,
                    onRefresh = viewModel::refresh,
                )
            }
            if (state.fromCache) {
                item { AccountsNotice(stringResource(R.string.accounts_detail_cached), false) }
            } else if (account != null && account.usage == null) {
                item { AccountsNotice(stringResource(R.string.accounts_usage_unavailable), false) }
            }
            when {
                state.loading -> {
                    item {
                        ContentStateCard(
                            message = stringResource(R.string.content_loading),
                            loading = true,
                        )
                    }
                }
                account == null -> {
                    item { ContentStateCard(stringResource(R.string.accounts_detail_unavailable), isError = true) }
                }
                else -> {
                    item { AccountIdentitySummary(account) }
                    item {
                        Column {
                            AccountQuotaCard(account)
                            if (account.usage != null) {
                                AccountUsageCard(account, state.usageFromMs, state.usageToMs)
                            }
                            if (
                                account.shouldShowResetCredits() ||
                                state.resetCreditAction.phase != ResetCreditActionPhase.Idle
                            ) {
                                AccountResetCreditsCard(
                                    account = account,
                                    action = state.resetCreditAction,
                                    allowAction = !state.fromCache,
                                    onUse = { viewModel.requestResetCredit(account.stableId) },
                                )
                            }
                            AccountDataDetailsCard(account, state.observedAtMs)
                        }
                    }
                }
            }
        }
    }

    if (account != null && state.resetCreditAction.phase == ResetCreditActionPhase.Confirming) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissResetCreditConfirmation(account.stableId) },
            title = { Text(stringResource(R.string.accounts_reset_credits_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.accounts_reset_credits_confirm_body,
                        state.resetCreditAction.availableCount,
                        account.displayTitle(),
                    ),
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.confirmResetCredit(account.stableId) }) {
                    Text(stringResource(R.string.accounts_reset_credits_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissResetCreditConfirmation(account.stableId) }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun AccountDetailHeader(
    onBack: () -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
) {
    PageHeader(
        eyebrow = stringResource(R.string.nav_accounts),
        title = stringResource(R.string.accounts_detail_title),
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
                contentDescription = stringResource(R.string.accounts_refresh_quotas),
                loading = refreshing,
                onClick = onRefresh,
            )
        },
    )
}

@Composable
private fun AccountProviderFilters(
    providers: List<String>,
    selectedProvider: String?,
    accountCounts: Map<String, Int>,
    totalCount: Int,
    onProviderSelected: (String?) -> Unit,
) {
    val filters = listOf<String?>(null) + providers
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        filters.forEach { provider ->
            AccountProviderTab(
                label = provider?.let {
                    stringResource(R.string.accounts_filter_provider, providerLabel(it))
                } ?: stringResource(R.string.accounts_filter_all),
                count = provider?.let { accountCounts[it] } ?: totalCount,
                selected = selectedProvider == provider,
                onClick = { onProviderSelected(provider) },
                leadingIcon = provider?.let {
                    { CredentialProviderIcon(it, modifier = Modifier.size(18.dp)) }
                },
            )
        }
    }
}

@Composable
private fun AccountProviderTab(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 36.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leadingIcon?.invoke()
            Text(
                label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
    containerColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
    content: @Composable () -> Unit,
) {
    AppCard(
        modifier = modifier,
        containerColor = containerColor,
        content = content,
    )
}

@Composable
private fun AccountPanel(content: @Composable () -> Unit) {
    AppCard(content = content)
}

@Composable
private fun AccountHealthOverview(accounts: List<AccountHealth>) {
    val states = accounts.map(AccountHealth::overviewState)
    val metrics = listOf(
        AccountHealthMetric(
            label = R.string.accounts_overview_total,
            count = accounts.size,
            color = MaterialTheme.colorScheme.onSurface,
        ),
        AccountHealthMetric(
            label = R.string.accounts_overview_healthy,
            count = states.count { it == AccountOverviewState.Healthy },
            color = MaterialTheme.colorScheme.tertiary,
        ),
        AccountHealthMetric(
            label = R.string.accounts_overview_attention,
            count = states.count { it != AccountOverviewState.Healthy },
            color = QuotaOrange,
        ),
    )
    AccountCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            metrics.forEachIndexed { index, metric ->
                AccountHealthMetricItem(metric, Modifier.weight(1f))
                if (index < metrics.lastIndex) {
                    Box(
                        Modifier.width(1.dp).height(44.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountHealthMetricItem(metric: AccountHealthMetric, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            metric.count.toString(),
            style = MaterialTheme.typography.titleLarge,
            color = metric.color,
            fontWeight = FontWeight.Bold,
        )
        Text(
            stringResource(metric.label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private data class AccountHealthMetric(
    @StringRes val label: Int,
    val count: Int,
    val color: Color,
)

internal enum class AccountOverviewState { Healthy, NeedsAttention, QuotaRisk, Disabled, Pending }

internal fun AccountHealth.overviewState(): AccountOverviewState = when {
    status == AccountStatus.Disabled -> AccountOverviewState.Disabled
    failure != null -> AccountOverviewState.NeedsAttention
    quotaState != AccountQuotaState.Available -> AccountOverviewState.Pending
    quotaLevel(minimumRemainingPercent()) == QuotaLevel.Healthy -> AccountOverviewState.Healthy
    quotaLevel(minimumRemainingPercent()) in setOf(QuotaLevel.Warning, QuotaLevel.Critical) ->
        AccountOverviewState.QuotaRisk
    else -> AccountOverviewState.Pending
}

@Composable
private fun AccountsNotice(message: String, isError: Boolean) {
    AccountCard(
        containerColor = if (isError) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            message,
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun AccountSectionTitle(@StringRes label: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(label), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            stringResource(R.string.accounts_tap_for_details),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AccountSummaryCard(account: AccountHealth, onClick: () -> Unit) {
    val plan = account.planType.trim().takeIf(String::isNotEmpty)
    AccountCard {
        Column(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AccountListProviderMark(account.provider)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        account.displayTitle(),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(providerLabel(account.provider), plan).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AccountSummaryStatusBadge(account.status)
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = stringResource(R.string.accounts_open_details),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AccountDetailDivider()
            if (account.status == AccountStatus.Disabled) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.accounts_quota_status),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.accounts_quota_hidden_disabled),
                        modifier = Modifier.weight(1f).padding(start = 16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.End,
                    )
                }
            } else {
                AccountCompactQuotaStack(account)
            }
        }
    }
}

@Composable
private fun AccountCompactQuotaStack(account: AccountHealth, modifier: Modifier = Modifier) {
    val windows = account.windows.take(2)
    if (windows.isEmpty()) {
        return
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        windows.forEach { window ->
            val remaining = normalizedRemainingPercent(window.remainingPercent)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    window.compactLabel(),
                    modifier = Modifier.width(62.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                QuotaProgressBar(
                    remainingPercent = remaining,
                    modifier = Modifier.weight(1f).height(6.dp),
                )
                Text(
                    remaining?.let { stringResource(R.string.accounts_quota_remaining_value, it) }
                        ?: "--",
                    modifier = Modifier.width(38.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = quotaLevelColor(quotaLevel(remaining)),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun AccountListProviderMark(provider: String) {
    val color = when (provider.normalizedProvider()) {
        "codex", "openai", "xai", "grok" -> MaterialTheme.colorScheme.onSurface
        "claude", "anthropic" -> Color(0xFFD06339)
        "gemini", "gemini-cli", "aistudio", "vertex", "antigravity" -> Color(0xFF0F8B86)
        else -> providerAccentColor(provider)
    }
    val label = when (provider.normalizedProvider()) {
        "codex", "openai" -> "OA"
        "claude", "anthropic" -> "A"
        "gemini", "gemini-cli", "aistudio", "vertex", "antigravity" -> "G"
        else -> providerBadgeLabel(provider).take(2).uppercase()
    }
    Surface(
        modifier = Modifier.size(44.dp),
        color = color,
        contentColor = Color.White,
        shape = MaterialTheme.shapes.small,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun AccountSummaryStatusBadge(status: AccountStatus) {
    val active = status == AccountStatus.Active
    val color = if (active) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline
    Surface(
        color = color.copy(alpha = 0.10f),
        contentColor = color,
        shape = RoundedCornerShape(50),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(color))
            Text(
                stringResource(
                    if (active) R.string.credential_quota_active
                    else R.string.credential_quota_disabled,
                ),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun AccountProviderBadge(provider: String) {
    val accent = providerAccentColor(provider)
    Surface(
        color = accent.copy(alpha = 0.10f),
        contentColor = accent,
        shape = MaterialTheme.shapes.extraSmall,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f)),
    ) {
        Text(
            providerBadgeLabel(provider),
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun AccountPlanBadge(plan: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
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
private fun AccountIdentitySummary(account: AccountHealth) {
    val metadata = listOf(providerLabel(account.provider), account.planType.trim())
        .filter(String::isNotEmpty)
        .joinToString(" · ")
    AccountPanel {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val accent = providerAccentColor(account.provider)
            Surface(
                color = accent.copy(alpha = 0.12f),
                contentColor = accent,
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)),
            ) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    CredentialProviderIcon(account.provider, modifier = Modifier.size(24.dp))
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    account.displayTitle(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    metadata,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AccountStatusBadge(account.status)
        }
    }
}

@Composable
private fun AccountStatusBadge(status: AccountStatus) {
    val active = status == AccountStatus.Active
    val color = if (active) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline
    Surface(
        color = color.copy(alpha = 0.10f),
        contentColor = color,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, color.copy(alpha = 0.22f)),
    ) {
        Text(
            stringResource(
                if (active) R.string.credential_quota_active
                else R.string.credential_quota_disabled,
            ),
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun AccountDataDetailsCard(account: AccountHealth, observedAtMs: Long?) {
    AccountDetailSection(R.string.accounts_data_details) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
            AccountDetailValue(R.string.accounts_source, stringResource(account.source.labelResource()))
            observedAtMs?.let {
                AccountDetailDivider()
                AccountDetailValue(R.string.accounts_observed_at, it.asDateTime())
            }
        }
    }
}

@Composable
private fun AccountDetailSection(
    @StringRes title: Int,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                stringResource(title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            subtitle?.let {
                Text(
                    it,
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                )
            }
        }
        content()
        Spacer(Modifier.height(12.dp))
        AccountDetailDivider()
    }
}

@Composable
private fun AccountDetailDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

@Composable
private fun AccountDetailValue(@StringRes label: Int, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            modifier = Modifier.weight(1f).padding(start = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun AccountUsageCard(account: AccountHealth, fromMs: Long?, toMs: Long?) {
    val usage = account.usage ?: return
    val projectedCost = account.estimatedQuotaCycleCost()
    val metrics = listOf(
        AccountUsageMetric(
            R.string.usage_requests,
            usage.calls.compactNumber(),
        ),
        AccountUsageMetric(
            R.string.usage_cost,
            usage.cost.asCost(),
        ),
        AccountUsageMetric(
            R.string.usage_tokens,
            usage.totalTokens.compactTokens(),
        ),
        AccountUsageMetric(
            R.string.health_success_rate,
            usage.successRate.asPercent(),
        ),
    )
    val range = if (fromMs != null && toMs != null) {
        stringResource(R.string.accounts_usage_range, fromMs.asDateTime(), toMs.asDateTime())
    } else {
        null
    }
    AccountDetailSection(R.string.accounts_usage, range) {
        Column(Modifier.fillMaxWidth()) {
            val rows = metrics.chunked(2)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column {
                    rows.forEachIndexed { rowIndex, rowMetrics ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            rowMetrics.forEachIndexed { columnIndex, metric ->
                                AccountUsageMetricCell(metric, Modifier.weight(1f))
                                if (columnIndex < rowMetrics.lastIndex) {
                                    Box(
                                        Modifier.width(1.dp).heightIn(min = 72.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant),
                                    )
                                }
                            }
                            if (rowMetrics.size == 1) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                        if (rowIndex < rows.lastIndex) {
                            AccountDetailDivider()
                        }
                    }
                }
            }
            projectedCost?.let { cost ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.accounts_usage_projected_cost),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        cost.asCost(),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountResetCreditsCard(
    account: AccountHealth,
    action: ResetCreditActionUiState,
    allowAction: Boolean,
    onUse: () -> Unit,
) {
    val available = account.resetCreditsAvailable
        ?: account.resetCredits.size.takeIf { it > 0 }
    val expiries = account.resetCredits
        .filter { it.expiresAtMs > System.currentTimeMillis() }
        .sortedBy { it.expiresAtMs }
    val busy = action.phase in setOf(
        ResetCreditActionPhase.Verifying,
        ResetCreditActionPhase.Redeeming,
    )
    AccountDetailSection(
        title = R.string.accounts_reset_credits,
        subtitle = stringResource(R.string.accounts_reset_credits_subtitle),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AccountMetricIcon(Icons.Outlined.Refresh, MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        available?.let {
                            stringResource(R.string.accounts_reset_credits_available, it)
                        } ?: stringResource(R.string.accounts_reset_credits_unknown),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.accounts_reset_credits_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = onUse,
                    enabled = allowAction &&
                        account.status == AccountStatus.Active &&
                        available != null &&
                        available > 0 &&
                        !busy,
                ) {
                    Text(
                        stringResource(
                            when (action.phase) {
                                ResetCreditActionPhase.Verifying -> R.string.accounts_reset_credits_verifying
                                ResetCreditActionPhase.Redeeming -> R.string.accounts_reset_credits_redeeming
                                else -> R.string.accounts_reset_credits_use
                            },
                        ),
                    )
                }
            }
            if (expiries.isNotEmpty()) {
                AccountDetailDivider()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.accounts_reset_credits_expiry),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                    expiries.forEach { credit ->
                        Text(
                            credit.expiresAtMs.asDateTime(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            resetCreditActionMessage(action.phase)?.let { message ->
                AccountDetailDivider()
                Text(
                    stringResource(message),
                    style = MaterialTheme.typography.bodySmall,
                    color = when (action.phase) {
                        ResetCreditActionPhase.Failed -> MaterialTheme.colorScheme.error
                        ResetCreditActionPhase.Success -> MaterialTheme.colorScheme.tertiary
                        ResetCreditActionPhase.PartialSuccess -> QuotaOrange
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@StringRes
private fun resetCreditActionMessage(phase: ResetCreditActionPhase): Int? = when (phase) {
    ResetCreditActionPhase.NoCredits -> R.string.accounts_reset_credits_none
    ResetCreditActionPhase.Failed -> R.string.accounts_reset_credits_failed
    ResetCreditActionPhase.Success -> R.string.accounts_reset_credits_success
    ResetCreditActionPhase.PartialSuccess -> R.string.accounts_reset_credits_partial_success
    else -> null
}

private data class AccountUsageMetric(
    @param:StringRes val label: Int,
    val value: String,
)

@Composable
private fun AccountUsageMetricCell(metric: AccountUsageMetric, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.heightIn(min = 72.dp).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(metric.label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            metric.value,
            modifier = Modifier.padding(top = 4.dp),
            style = when {
                metric.value.length <= 10 -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.bodyLarge
            },
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AccountMetricIcon(icon: ImageVector, color: Color) {
    Surface(
        color = color.copy(alpha = 0.10f),
        contentColor = color,
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun AccountQuotaCard(account: AccountHealth) {
    AccountDetailSection(R.string.accounts_quota) {
        Column(Modifier.fillMaxWidth()) {
            when {
                account.windows.isNotEmpty() -> {
                    account.failure?.takeIf { account.status != AccountStatus.Disabled }?.let { failure ->
                        Text(
                            stringResource(failure.messageResource()),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        AccountDetailDivider()
                    }
                    if (account.status == AccountStatus.Disabled) {
                        Text(
                            stringResource(R.string.credential_quota_disabled_with_windows_hint),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        AccountDetailDivider()
                    }
                    account.windows.forEachIndexed { index, window ->
                        AccountQuotaWindowRow(window)
                        if (index < account.windows.lastIndex) {
                            AccountDetailDivider()
                        }
                    }
                }
                account.status == AccountStatus.Disabled -> AccountQuotaEmptyState(
                    stringResource(R.string.credential_quota_disabled_hint),
                )
                account.quotaState == AccountQuotaState.Failed -> AccountQuotaEmptyState(
                    stringResource(account.failure.messageResource()),
                    MaterialTheme.colorScheme.error,
                )
                account.quotaState == AccountQuotaState.NotRequested -> AccountQuotaEmptyState(
                    stringResource(R.string.accounts_quota_not_refreshed),
                )
                account.quotaState == AccountQuotaState.Unsupported -> AccountQuotaEmptyState(
                    stringResource(R.string.accounts_quota_unsupported),
                )
                else -> AccountQuotaEmptyState(stringResource(R.string.credential_quota_no_windows))
            }
        }
    }
}

@Composable
private fun AccountQuotaEmptyState(
    message: String,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        message,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 10.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = color,
    )
}

@Composable
private fun AccountQuotaWindowRow(window: AccountQuotaWindow) {
    val remaining = normalizedRemainingPercent(window.remainingPercent)
    val reset = window.resetAtMs?.takeIf { it > 0 }?.asDateTime()
        ?: window.resetLabel.trim().takeIf { it.isNotEmpty() && it != "-" }
    val durationLabel = window.durationLabel()
    val windowTitle = window.label.trim().takeIf(String::isNotEmpty)?.let { label ->
        if (window.durationSeconds > 0) "$label · $durationLabel" else label
    } ?: durationLabel
    val levelColor = quotaLevelColor(quotaLevel(remaining))
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                windowTitle,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                remaining?.let { stringResource(R.string.accounts_quota_remaining_value, it) }
                    ?: stringResource(R.string.credential_quota_remaining_unknown),
                style = MaterialTheme.typography.titleMedium,
                color = levelColor,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
            )
        }
        QuotaProgressBar(remaining, modifier = Modifier.fillMaxWidth().height(8.dp))
        reset?.let {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.credential_quota_reset, it),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun QuotaProgressBar(remainingPercent: Double?, modifier: Modifier = Modifier) {
    val normalized = normalizedRemainingPercent(remainingPercent)
    val progress = normalized?.div(100.0)?.toFloat()
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
    ) {
        progress?.let {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(it.coerceAtLeast(MinimumVisibleQuotaProgress))
                    .background(quotaLevelColor(quotaLevel(normalized))),
            )
        }
    }
}

private fun AccountHealth.minimumRemainingPercent(): Double? =
    windows.mapNotNull { normalizedRemainingPercent(it.remainingPercent) }.minOrNull()

private fun AccountHealth.shouldShowResetCredits(): Boolean =
    provider.normalizedProvider() == "codex" &&
        (resetCreditsAvailable != null || resetCredits.isNotEmpty())

internal fun normalizedRemainingPercent(remainingPercent: Double?): Double? =
    remainingPercent?.takeIf(Double::isFinite)?.coerceIn(0.0, 100.0)

private fun String.normalizedProvider(): String = trim().lowercase()

private fun String.isOpenAiProvider(): Boolean = normalizedProvider() in setOf("codex", "openai")

internal fun AccountHealth.displayTitle(): String = account.trim().ifBlank { name.trim() }.ifBlank { providerLabel(provider) }

internal fun providerLabel(provider: String): String = when (provider.trim().lowercase()) {
    "codex", "openai" -> "OpenAI"
    "xai", "grok" -> "xAI"
    "claude", "anthropic" -> "Anthropic"
    "gemini", "gemini-cli", "aistudio", "vertex", "antigravity" -> "Google"
    "qwen" -> "Qwen"
    "deepseek" -> "DeepSeek"
    "kimi" -> "Kimi"
    else -> provider.trim().replaceFirstChar { it.titlecase() }.ifBlank { "AI" }
}

internal fun providerBadgeLabel(provider: String): String = when (provider.trim().lowercase()) {
    "codex", "openai" -> "OpenAI"
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

internal fun quotaLevel(remainingPercent: Double?): QuotaLevel {
    val normalized = normalizedRemainingPercent(remainingPercent)
    return when {
        normalized == null -> QuotaLevel.Unknown
        normalized >= 70.0 -> QuotaLevel.Healthy
        normalized >= 30.0 -> QuotaLevel.Warning
        else -> QuotaLevel.Critical
    }
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
    durationSeconds > 0 && durationSeconds % SECONDS_PER_DAY == 0L ->
        stringResource(R.string.credential_quota_window_days, durationSeconds / SECONDS_PER_DAY)
    durationSeconds > 0 && durationSeconds % SECONDS_PER_HOUR == 0L ->
        stringResource(R.string.credential_quota_window_hours, durationSeconds / SECONDS_PER_HOUR)
    else -> stringResource(R.string.credential_quota_window_other)
}

@Composable
private fun AccountQuotaWindow.compactLabel(): String = when {
    durationSeconds > 0 && durationSeconds % SECONDS_PER_DAY == 0L ->
        stringResource(R.string.accounts_quota_compact_days, durationSeconds / SECONDS_PER_DAY)
    durationSeconds > 0 && durationSeconds % SECONDS_PER_HOUR == 0L ->
        stringResource(R.string.accounts_quota_compact_hours, durationSeconds / SECONDS_PER_HOUR)
    else -> label.trim().ifBlank { stringResource(R.string.credential_quota_window_other) }
}

internal val QuotaOrange = androidx.compose.ui.graphics.Color(0xFFF59E0B)

private const val MinimumVisibleQuotaProgress = 0.02f

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
