package com.cpamp.mobile.ui.monitoring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cpamp.mobile.common.runSuspendCatching
import com.cpamp.mobile.data.auth.SessionRepository
import com.cpamp.mobile.data.monitoring.CredentialQuota
import com.cpamp.mobile.data.monitoring.CredentialQuotaRepository
import com.cpamp.mobile.data.monitoring.NoCompletedInspectionException
import com.cpamp.mobile.data.remote.RemoteFailure
import com.cpamp.mobile.data.system.ServerVersionObserver
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CredentialQuotaUiState(
    val quotas: List<CredentialQuota> = emptyList(),
    val runId: Long? = null,
    val finishedAtMs: Long? = null,
    val serverVersion: String? = null,
    val loaded: Boolean = false,
    val fromCache: Boolean = false,
    val loading: Boolean = false,
    val error: CredentialQuotaError? = null,
)

enum class CredentialQuotaError { NoCompletedInspection, Unauthorized, ServerUnsupported, InvalidResponse, Network, Server }

@HiltViewModel
class CredentialQuotaViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val repository: CredentialQuotaRepository,
    private val serverVersionObserver: ServerVersionObserver,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CredentialQuotaUiState())
    val state: StateFlow<CredentialQuotaUiState> = mutableState.asStateFlow()

    fun loadIfNeeded() {
        if (mutableState.value.loaded || mutableState.value.loading) return
        refresh()
    }

    fun refresh() {
        if (mutableState.value.loading) return
        val session = sessionRepository.session.value ?: return
        mutableState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runSuspendCatching { repository.load(session) }
                .onSuccess { snapshot ->
                    if (sessionRepository.session.value?.profile?.id != session.profile.id) return@onSuccess
                    mutableState.value = CredentialQuotaUiState(
                        quotas = snapshot.quotas,
                        runId = snapshot.runId,
                        finishedAtMs = snapshot.finishedAtMs,
                        loaded = true,
                        fromCache = snapshot.fromCache,
                    )
                }
                .onFailure { error ->
                    if (sessionRepository.session.value?.profile?.id != session.profile.id) return@onFailure
                    val cached = repository.cached(session.profile.id)
                    mutableState.value = cached?.let { snapshot ->
                        CredentialQuotaUiState(
                            quotas = snapshot.quotas,
                            runId = snapshot.runId,
                            finishedAtMs = snapshot.finishedAtMs,
                            serverVersion = serverVersionObserver.snapshot(session.profile.id).cpampVersion,
                            loaded = true,
                            fromCache = true,
                            error = error.toCredentialQuotaError(),
                        )
                    } ?: CredentialQuotaUiState(
                        serverVersion = serverVersionObserver.snapshot(session.profile.id).cpampVersion,
                        loaded = true,
                        error = error.toCredentialQuotaError(),
                    )
                }
        }
    }
}

private fun Throwable.toCredentialQuotaError(): CredentialQuotaError = when (this) {
    is NoCompletedInspectionException -> CredentialQuotaError.NoCompletedInspection
    is RemoteFailure.Unauthorized -> CredentialQuotaError.Unauthorized
    is RemoteFailure.NotFound -> CredentialQuotaError.ServerUnsupported
    is RemoteFailure.InvalidResponse -> CredentialQuotaError.InvalidResponse
    is RemoteFailure.Network, is RemoteFailure.Timeout, is RemoteFailure.Tls -> CredentialQuotaError.Network
    else -> CredentialQuotaError.Server
}
