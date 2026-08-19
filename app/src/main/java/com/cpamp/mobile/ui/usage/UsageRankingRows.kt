package com.cpamp.mobile.ui.usage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cpamp.mobile.R
import com.cpamp.mobile.data.remote.model.ApiKeyStatDto
import com.cpamp.mobile.data.remote.model.CredentialStatDto
import com.cpamp.mobile.data.remote.model.ModelStatDto
import com.cpamp.mobile.data.remote.model.MonitoringResponseDto
import com.cpamp.mobile.ui.common.asCost
import com.cpamp.mobile.ui.common.asPercent
import com.cpamp.mobile.ui.common.compactNumber
import com.cpamp.mobile.ui.common.compactTokens
import com.cpamp.mobile.ui.components.AppCard
import com.cpamp.mobile.ui.components.ModelProviderIcon

internal fun LazyListScope.usageRankingItems(
    ranking: UsageRanking,
    response: MonitoringResponseDto,
) {
    item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (ranking) {
                UsageRanking.Models -> response.modelStats.sortedByDescending(ModelStatDto::calls).take(10).forEach {
                    RankingRow(it.model, it.calls, it.totalTokens, it.cost, it.successRate, model = it.model)
                }
                UsageRanking.ApiKeys -> response.apiKeyStats.sortedByDescending(ApiKeyStatDto::calls).take(10).forEach {
                    RankingRow(it.displayName, it.calls, it.totalTokens, it.cost, it.successRate)
                }
                UsageRanking.Credentials -> response.credentialStats
                    .sortedByDescending(CredentialStatDto::calls)
                    .take(10)
                    .forEach {
                        RankingRow(it.displayName, it.calls, it.totalTokens, it.cost, it.successRate)
                    }
            }
        }
    }
}

@Composable
private fun RankingRow(
    name: String,
    calls: Long,
    tokens: Long,
    cost: Double,
    successRate: Double,
    model: String? = null,
) {
    AppCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            model?.let { ModelProviderIcon(it) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    name.ifBlank { stringResource(R.string.unknown_value) },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        stringResource(R.string.usage_calls_value, calls.compactNumber()),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(tokens.compactTokens(), style = MaterialTheme.typography.bodySmall)
                    Text(cost.asCost(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    Text(successRate.asPercent(), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private val ApiKeyStatDto.displayName: String
    get() = authLabelSnapshot.ifBlank { accountSnapshot.ifBlank { apiKeyHash.ifBlank { id } } }

private val CredentialStatDto.displayName: String
    get() = authLabelSnapshot.ifBlank {
        accountSnapshot.ifBlank { authFileSnapshot.ifBlank { authIndex.ifBlank { id } } }
    }
