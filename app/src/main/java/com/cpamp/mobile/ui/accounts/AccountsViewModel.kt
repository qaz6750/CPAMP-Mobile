package com.cpamp.mobile.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cpamp.mobile.common.runSuspendCatching
import com.cpamp.mobile.data.accounts.AccountHealth
import com.cpamp.mobile.data.accounts.AccountHealthRepository
import com.cpamp.mobile.data.accounts.AccountHealthSnapshot
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
)

data class AccountDetailUiState(
    val account: AccountHealth? = null,
    val observedAtMs: Long? = null,
    val fromCache: Boolean = false,
)

enum class AccountsError { Unauthorized, RateLimited, Network, Server }

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
    ) { session, snapshots ->
        val snapshot = session?.profile?.id?.let(snapshots::get)
        AccountDetailUiState(
            account = snapshot?.accountForDetail(accountId),
            observedAtMs = snapshot?.observedAtMs,
            fromCache = snapshot?.fromCache == true,
        )
    }.scan(AccountDetailUiState()) { previous, current ->
        retainCachedAccountDetail(previous, current, accountId)
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
