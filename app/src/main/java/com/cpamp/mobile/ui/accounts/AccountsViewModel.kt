package com.cpamp.mobile.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cpamp.mobile.common.runSuspendCatching
import com.cpamp.mobile.data.accounts.AccountHealth
import com.cpamp.mobile.data.accounts.AccountHealthRepository
import com.cpamp.mobile.data.accounts.AccountHealthSnapshot
import com.cpamp.mobile.data.accounts.AccountUsageState
import com.cpamp.mobile.data.accounts.accountForDetail
import com.cpamp.mobile.data.auth.SessionRepository
import com.cpamp.mobile.data.remote.RemoteFailure
import com.cpamp.mobile.domain.model.ServerProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountsUiState(
    val profile: ServerProfile? = null,
    val snapshot: AccountHealthSnapshot? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: AccountsError? = null,
    val resetCreditAction: ResetCreditActionUiState = ResetCreditActionUiState(),
)

data class AccountDetailUiState(
    val account: AccountHealth? = null,
    val observedAtMs: Long? = null,
    val usageState: AccountUsageState = AccountUsageState.Unavailable,
    val usageFromMs: Long? = null,
    val usageToMs: Long? = null,
    val fromCache: Boolean = false,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val resetCreditAction: ResetCreditActionUiState = ResetCreditActionUiState(),
)

enum class AccountsError { Unauthorized, RateLimited, Network, Server }

enum class ResetCreditActionPhase {
    Idle,
    Verifying,
    Confirming,
    Redeeming,
    Success,
    PartialSuccess,
    NoCredits,
    Failed,
}

data class ResetCreditActionUiState(
    val accountId: String = "",
    val phase: ResetCreditActionPhase = ResetCreditActionPhase.Idle,
    val availableCount: Int = 0,
)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val repository: AccountHealthRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AccountsUiState())
    val state: StateFlow<AccountsUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionRepository.session.collectLatest { session ->
                if (session == null) {
                    mutableState.value = AccountsUiState(loading = false)
                    return@collectLatest
                }
                mutableState.value = AccountsUiState(profile = session.profile, loading = true)
                repository.cached(session.profile.id)?.let { cached ->
                    mutableState.update { it.copy(snapshot = cached, loading = false) }
                }
                load()
            }
        }
    }

    fun refresh() {
        if (mutableState.value.loading || mutableState.value.refreshing) return
        viewModelScope.launch { load() }
    }

    fun detail(accountId: String): Flow<AccountDetailUiState> = combine(
        sessionRepository.session,
        repository.snapshots,
        state,
    ) { session, snapshots, accountsState ->
        val snapshot = session?.profile?.id?.let(snapshots::get)
        val account = snapshot?.accountForDetail(accountId)
        AccountDetailUiState(
            account = account,
            observedAtMs = snapshot?.observedAtMs,
            usageState = account?.usageState ?: AccountUsageState.Unavailable,
            usageFromMs = account?.usageFromMs?.takeIf { it > 0 },
            usageToMs = account?.usageToMs?.takeIf { it > 0 },
            fromCache = snapshot?.fromCache == true,
            loading = accountsState.loading && snapshot == null,
            refreshing = accountsState.refreshing,
            resetCreditAction = accountsState.resetCreditAction.takeIf {
                it.accountId == accountId
            } ?: ResetCreditActionUiState(),
        )
    }.scan(AccountDetailUiState(loading = true)) { previous, current ->
        retainCachedAccountDetail(previous, current, accountId)
    }

    fun requestResetCredit(accountId: String) {
        if (mutableState.value.resetCreditAction.phase in activeResetCreditPhases) return
        viewModelScope.launch {
            mutableState.update {
                it.copy(
                    resetCreditAction = ResetCreditActionUiState(
                        accountId = accountId,
                        phase = ResetCreditActionPhase.Verifying,
                    ),
                )
            }
            val session = sessionRepository.session.value
            if (session == null) {
                updateResetCreditAction(accountId, ResetCreditActionPhase.Failed)
                return@launch
            }
            runSuspendCatching { repository.refreshCredentialQuota(session, accountId) }
                .onSuccess { account ->
                    if (sessionRepository.session.value?.profile?.id != session.profile.id) return@onSuccess
                    val count = account.resetCreditsAvailable
                        ?: account.resetCredits.size.takeIf { it > 0 }
                    if (count == null) {
                        updateResetCreditAction(accountId, ResetCreditActionPhase.Failed)
                        return@onSuccess
                    }
                    mutableState.update {
                        it.copy(
                            error = null,
                            resetCreditAction = ResetCreditActionUiState(
                                accountId = accountId,
                                phase = if (count > 0) {
                                    ResetCreditActionPhase.Confirming
                                } else {
                                    ResetCreditActionPhase.NoCredits
                                },
                                availableCount = count,
                            ),
                        )
                    }
                }
                .onFailure {
                    updateResetCreditAction(accountId, ResetCreditActionPhase.Failed)
                }
        }
    }

    fun dismissResetCreditConfirmation(accountId: String) {
        if (mutableState.value.resetCreditAction.accountId != accountId) return
        updateResetCreditAction(accountId, ResetCreditActionPhase.Idle)
    }

    fun confirmResetCredit(accountId: String) {
        val action = mutableState.value.resetCreditAction
        if (action.accountId != accountId || action.phase != ResetCreditActionPhase.Confirming) return
        viewModelScope.launch {
            updateResetCreditAction(
                accountId = accountId,
                phase = ResetCreditActionPhase.Redeeming,
                availableCount = action.availableCount,
            )
            val session = sessionRepository.session.value
            if (session == null) {
                updateResetCreditAction(accountId, ResetCreditActionPhase.Failed)
                return@launch
            }
            val consumeResult = runSuspendCatching {
                repository.consumeCodexResetCredit(session, accountId)
            }
            if (consumeResult.isFailure) {
                updateResetCreditAction(accountId, ResetCreditActionPhase.Failed)
                return@launch
            }
            runSuspendCatching { repository.refreshCredentialQuota(session, accountId) }
                .onSuccess {
                    if (sessionRepository.session.value?.profile?.id != session.profile.id) return@onSuccess
                    mutableState.update {
                        it.copy(
                            error = null,
                            resetCreditAction = ResetCreditActionUiState(
                                accountId = accountId,
                                phase = ResetCreditActionPhase.Success,
                            ),
                        )
                    }
                }
                .onFailure {
                    updateResetCreditAction(accountId, ResetCreditActionPhase.PartialSuccess)
                }
        }
    }

    private fun updateResetCreditAction(
        accountId: String,
        phase: ResetCreditActionPhase,
        availableCount: Int = 0,
    ) {
        mutableState.update {
            it.copy(
                resetCreditAction = ResetCreditActionUiState(
                    accountId = accountId,
                    phase = phase,
                    availableCount = availableCount,
                ),
            )
        }
    }

    private suspend fun load() {
        val session = sessionRepository.session.value ?: return
        mutableState.update { state ->
            state.copy(
                loading = state.snapshot == null,
                refreshing = state.snapshot != null,
                error = null,
            )
        }
        runSuspendCatching { repository.load(session) }
            .onSuccess { snapshot ->
                if (sessionRepository.session.value?.profile?.id != session.profile.id) return@onSuccess
                mutableState.update {
                    it.copy(
                        profile = session.profile,
                        snapshot = snapshot,
                        loading = false,
                        refreshing = false,
                        error = null,
                    )
                }
            }
            .onFailure { error ->
                if (sessionRepository.session.value?.profile?.id != session.profile.id) return@onFailure
                val cached = mutableState.value.snapshot ?: repository.cached(session.profile.id)
                mutableState.update {
                    it.copy(
                        snapshot = cached,
                        loading = false,
                        refreshing = false,
                        error = error.toAccountsError(),
                    )
                }
            }
    }
}

private val activeResetCreditPhases = setOf(
    ResetCreditActionPhase.Verifying,
    ResetCreditActionPhase.Confirming,
    ResetCreditActionPhase.Redeeming,
)

internal fun retainCachedAccountDetail(
    previous: AccountDetailUiState,
    current: AccountDetailUiState,
    accountId: String,
): AccountDetailUiState = if (
    current.account == null && previous.fromCache && previous.account?.stableId == accountId
) {
    previous
} else {
    current
}

private fun Throwable.toAccountsError(): AccountsError = when (this) {
    is RemoteFailure.Unauthorized -> AccountsError.Unauthorized
    is RemoteFailure.RateLimited -> AccountsError.RateLimited
    is RemoteFailure.Network, is RemoteFailure.Timeout, is RemoteFailure.Tls -> AccountsError.Network
    else -> AccountsError.Server
}
