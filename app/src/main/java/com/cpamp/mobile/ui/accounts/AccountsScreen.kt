package com.cpamp.mobile.ui.accounts

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Token
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalDensity
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
                        stringResource(R.string.accounts_source_inventory, snapshot.observedAtMs.asDateTime()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                    item { AccountSectionTitle(R.string.accounts_credentials, visibleAccounts.size) }
                    item {
                        AccountListPanel {
                            visibleAccounts.forEachIndexed { index, account ->
                                AccountSummaryRow(account) { onOpenAccount(account.stableId) }
                                if (index < visibleAccounts.lastIndex) {
                                    AccountDetailDivider()
                                }
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
                start = 20.dp,
                end = 20.dp,
                top = contentPadding.calculateTopPadding() + 18.dp,
                bottom = contentPadding.calculateBottomPadding() + 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AccountDetailHeader(onBack)
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
                    item { AccountQuotaCard(account) }
                    if (account.usage != null) {
                        item { AccountUsageCard(account, state.usageFromMs, state.usageToMs) }
                    }
                    item { AccountDataDetailsCard(account, state.observedAtMs) }
                }
            }
        }
    }
}

@Composable
private fun AccountDetailHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.back),
            )
        }
        Text(
            stringResource(R.string.accounts_detail_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
        contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
            else MaterialTheme.colorScheme.outlineVariant,
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
    containerColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
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
    val states = accounts.map(AccountHealth::overviewState)
    val metrics = listOf(
        AccountHealthMetric(
            label = R.string.accounts_overview_total,
            count = accounts.size,
            color = MaterialTheme.colorScheme.primary,
        ),
        AccountHealthMetric(
            label = R.string.accounts_overview_healthy,
            count = states.count { it == AccountOverviewState.Healthy },
            color = MaterialTheme.colorScheme.tertiary,
        ),
        AccountHealthMetric(
            label = R.string.accounts_overview_risk,
            count = states.count { it == AccountOverviewState.QuotaRisk },
            color = QuotaOrange,
        ),
        AccountHealthMetric(
            label = R.string.accounts_overview_attention,
            count = states.count {
                it in setOf(
                    AccountOverviewState.NeedsAttention,
                    AccountOverviewState.Disabled,
                    AccountOverviewState.Pending,
                )
            },
            color = MaterialTheme.colorScheme.error,
        ),
    )
    AccountListPanel {
        Column(modifier = Modifier.fillMaxWidth()) {
            metrics.chunked(2).forEachIndexed { rowIndex, rowMetrics ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    rowMetrics.forEachIndexed { columnIndex, metric ->
                        AccountHealthMetricItem(metric, Modifier.weight(1f))
                        if (columnIndex < rowMetrics.lastIndex) {
                            Box(
                                Modifier.width(1.dp).heightIn(min = 68.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant),
                            )
                        }
                    }
                }
                if (rowIndex < metrics.lastIndex / 2) {
                    AccountDetailDivider()
                }
            }
        }
    }
}

@Composable
private fun AccountHealthMetricItem(metric: AccountHealthMetric, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.heightIn(min = 68.dp).padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
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
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AccountListPanel(content: @Composable () -> Unit) {
    AccountCard {
        Column(Modifier.fillMaxWidth()) {
            content()
        }
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
private fun AccountSummaryRow(account: AccountHealth, onClick: () -> Unit) {
    val remaining = account.minimumRemainingPercent()
    val level = quotaLevel(remaining)
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(13.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val accent = providerAccentColor(account.provider)
        Surface(
            color = accent.copy(alpha = 0.10f),
            contentColor = accent,
            shape = RoundedCornerShape(8.dp),
        ) {
            Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                CredentialProviderIcon(account.provider, modifier = Modifier.size(23.dp))
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                account.displayTitle(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    providerBadgeLabel(account.provider),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                account.planType.trim().takeIf(String::isNotEmpty)?.let { plan ->
                    Text("· $plan", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                remaining?.let { value ->
                    Text(
                        "· ${stringResource(R.string.credential_quota_remaining, value)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = quotaLevelColor(level),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            AccountStatusBadge(account)
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = stringResource(R.string.accounts_open_details),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
    AccountPanel {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = maxWidth < 380.dp || LocalDensity.current.fontScale >= 1.3f
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val accent = providerAccentColor(account.provider)
                Surface(
                    color = accent.copy(alpha = 0.12f),
                    contentColor = accent,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        CredentialProviderIcon(account.provider, modifier = Modifier.size(26.dp))
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        account.displayTitle(),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AccountProviderBadge(account.provider)
                        account.planType.trim().takeIf(String::isNotEmpty)?.let { plan ->
                            AccountPlanBadge(plan, Modifier.weight(1f, fill = false))
                        }
                    }
                    if (compact) {
                        AccountStatusBadge(account)
                    }
                }
                if (!compact) {
                    AccountStatusBadge(account)
                }
            }
        }
    }
}

@Composable
private fun AccountDataDetailsCard(account: AccountHealth, observedAtMs: Long?) {
    AccountDetailSection(R.string.accounts_data_details) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp)) {
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
    AccountPanel {
        Column(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    stringResource(title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            AccountDetailDivider()
            content()
        }
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
            Icons.Outlined.DataUsage,
            MaterialTheme.colorScheme.primary,
        ),
        AccountUsageMetric(
            R.string.usage_tokens,
            usage.totalTokens.compactTokens(),
            Icons.Outlined.Token,
            MaterialTheme.colorScheme.primary,
        ),
        AccountUsageMetric(
            R.string.usage_cost,
            usage.cost.asCost(),
            Icons.Outlined.Payments,
            QuotaOrange,
        ),
        AccountUsageMetric(
            R.string.health_success_rate,
            usage.successRate.asPercent(),
            Icons.Outlined.CheckCircle,
            MaterialTheme.colorScheme.tertiary,
        ),
    )
    val range = if (fromMs != null && toMs != null) {
        stringResource(R.string.accounts_usage_range, fromMs.asDateTime(), toMs.asDateTime())
    } else {
        null
    }
    AccountDetailSection(R.string.accounts_usage, range) {
        Column(Modifier.fillMaxWidth()) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val dividerColor = MaterialTheme.colorScheme.outlineVariant
                val fontScale = LocalDensity.current.fontScale
                val columns = when {
                    fontScale >= 1.3f || maxWidth < 420.dp -> 1
                    maxWidth >= 720.dp -> 4
                    else -> 2
                }
                val rows = metrics.chunked(columns)
                Column {
                    rows.forEachIndexed { rowIndex, rowMetrics ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            rowMetrics.forEachIndexed { columnIndex, metric ->
                                AccountUsageMetricCell(metric, Modifier.weight(1f))
                                if (columnIndex < rowMetrics.lastIndex) {
                                    Box(Modifier.width(1.dp).heightIn(min = 90.dp).background(dividerColor))
                                }
                            }
                            repeat(columns - rowMetrics.size) {
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
                AccountDetailDivider()
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.04f))
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AccountMetricIcon(Icons.Outlined.TrendingUp, MaterialTheme.colorScheme.primary)
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

private data class AccountUsageMetric(
    @param:StringRes val label: Int,
    val value: String,
    val icon: ImageVector,
    val color: Color,
)

@Composable
private fun AccountUsageMetricCell(metric: AccountUsageMetric, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.heightIn(min = 90.dp).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AccountMetricIcon(metric.icon, metric.color)
            Text(
                stringResource(metric.label),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            metric.value,
            modifier = Modifier.padding(start = 38.dp),
            style = when {
                metric.value.length <= 10 -> MaterialTheme.typography.titleLarge
                metric.value.length <= 14 -> MaterialTheme.typography.titleMedium
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
        shape = RoundedCornerShape(8.dp),
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
                    account.failure?.let { failure ->
                        Text(
                            stringResource(failure.messageResource()),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        AccountDetailDivider()
                    }
                    if (account.status == AccountStatus.Disabled) {
                        Text(
                            stringResource(R.string.credential_quota_disabled_with_windows_hint),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
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
                account.quotaState == AccountQuotaState.Failed -> AccountQuotaEmptyState(
                    stringResource(account.failure.messageResource()),
                    MaterialTheme.colorScheme.error,
                )
                account.status == AccountStatus.Disabled -> AccountQuotaEmptyState(
                    stringResource(R.string.credential_quota_disabled_hint),
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
        modifier = Modifier.fillMaxWidth().padding(14.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = color,
    )
}

@Composable
private fun AccountQuotaWindowRow(window: AccountQuotaWindow) {
    val remaining = normalizedRemainingPercent(window.remainingPercent)
    val reset = window.resetAtMs?.takeIf { it > 0 }?.asDateTime()
        ?: window.resetLabel.trim().takeIf { it.isNotEmpty() && it != "-" }
    val levelColor = quotaLevelColor(quotaLevel(remaining))
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(levelColor))
                Text(
                    window.durationLabel(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    remaining?.let { stringResource(R.string.accounts_quota_remaining_value, it) }
                        ?: stringResource(R.string.credential_quota_remaining_unknown),
                    style = MaterialTheme.typography.titleLarge,
                    color = levelColor,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                )
                if (remaining != null) {
                    Text(
                        stringResource(R.string.accounts_quota_remaining_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
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

internal fun normalizedRemainingPercent(remainingPercent: Double?): Double? =
    remainingPercent?.takeIf(Double::isFinite)?.coerceIn(0.0, 100.0)

private fun String.normalizedProvider(): String = trim().lowercase()

@Composable
internal fun AccountStatusBadge(account: AccountHealth) {
    val (label, color) = when {
        account.status == AccountStatus.Disabled -> R.string.credential_quota_disabled_badge to MaterialTheme.colorScheme.onSurfaceVariant
        account.failure != null -> R.string.credential_quota_failed_badge to MaterialTheme.colorScheme.error
        account.quotaState == AccountQuotaState.Available &&
            quotaLevel(account.minimumRemainingPercent()) == QuotaLevel.Critical ->
            R.string.accounts_health_quota_critical to MaterialTheme.colorScheme.error
        account.quotaState == AccountQuotaState.Available &&
            quotaLevel(account.minimumRemainingPercent()) == QuotaLevel.Warning ->
            R.string.accounts_health_quota_warning to QuotaOrange
        account.quotaState == AccountQuotaState.Available && account.minimumRemainingPercent() == null ->
            R.string.accounts_health_basic to MaterialTheme.colorScheme.onSurfaceVariant
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
    label.isNotBlank() -> label
    durationSeconds > 0 && durationSeconds % SECONDS_PER_DAY == 0L ->
        stringResource(R.string.credential_quota_window_days, durationSeconds / SECONDS_PER_DAY)
    durationSeconds > 0 && durationSeconds % SECONDS_PER_HOUR == 0L ->
        stringResource(R.string.credential_quota_window_hours, durationSeconds / SECONDS_PER_HOUR)
    else -> stringResource(R.string.credential_quota_window_other)
}

internal val QuotaOrange = androidx.compose.ui.graphics.Color(0xFFF59E0B)

private const val MinimumVisibleQuotaProgress = 0.02f

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
