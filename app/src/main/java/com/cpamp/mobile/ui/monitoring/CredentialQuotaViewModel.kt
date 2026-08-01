package com.cpamp.mobile.ui.monitoring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cpamp.mobile.common.runSuspendCatching
import com.cpamp.mobile.data.auth.SessionRepository
import com.cpamp.mobile.data.monitoring.CredentialQuota
import com.cpamp.mobile.data.monitoring.CredentialQuotaRepository
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
    val inspectionStarting: Boolean = false,
    val inspectionStarted: Boolean = false,
    val inspectionAlreadyRunning: Boolean = false,
    val inspectionError: CredentialQuotaError? = null,
    val inspectionStatusCode: Int? = null,
)

enum class CredentialQuotaError {
    Unauthorized,
    ServerUnsupported,
    InvalidResponse,
    Network,
    RateLimited,
    Server,
}

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
                    mutableState.update { state ->
                        state.copy(
                            quotas = snapshot.quotas,
                            runId = snapshot.runId,
                            finishedAtMs = snapshot.finishedAtMs,
                            loaded = true,
                            fromCache = snapshot.fromCache,
                            loading = false,
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    if (sessionRepository.session.value?.profile?.id != session.profile.id) return@onFailure
                    val cached = repository.cached(session.profile.id)
                    mutableState.update { state ->
                        state.copy(
                            quotas = cached?.quotas ?: state.quotas,
                            runId = cached?.runId ?: state.runId,
                            finishedAtMs = cached?.finishedAtMs ?: state.finishedAtMs,
                            serverVersion = serverVersionObserver.snapshot(session.profile.id).cpampVersion,
                            loaded = true,
                            fromCache = cached != null,
                            loading = false,
                            error = error.toCredentialQuotaError(),
                        )
                    }
                }
        }
    }

    fun startInspection() {
        if (mutableState.value.inspectionStarting) return
        val session = sessionRepository.session.value ?: return
        mutableState.update {
            it.copy(
                inspectionStarting = true,
                inspectionStarted = false,
                inspectionAlreadyRunning = false,
                inspectionError = null,
                inspectionStatusCode = null,
            )
        }
        viewModelScope.launch {
            runSuspendCatching { repository.startInspection(session) }
                .onSuccess {
                    if (sessionRepository.session.value?.profile?.id != session.profile.id) return@onSuccess
                    mutableState.update { state ->
                        state.copy(inspectionStarting = false, inspectionStarted = true)
                    }
                }
                .onFailure { error ->
                    if (sessionRepository.session.value?.profile?.id != session.profile.id) return@onFailure
                    val statusCode = (error as? RemoteFailure.Server)?.statusCode
                    mutableState.update { state ->
                        when (statusCode) {
                            409 -> state.copy(
                                inspectionStarting = false,
                                inspectionAlreadyRunning = true,
                            )
                            405 -> state.copy(
                                inspectionStarting = false,
                                inspectionError = CredentialQuotaError.ServerUnsupported,
                            )
                            else -> state.copy(
                                inspectionStarting = false,
                                inspectionError = error.toCredentialQuotaError(),
                                inspectionStatusCode = statusCode,
                            )
                        }
                    }
                }
        }
    }
}

private fun Throwable.toCredentialQuotaError(): CredentialQuotaError = when (this) {
    is RemoteFailure.Unauthorized -> CredentialQuotaError.Unauthorized
    is RemoteFailure.NotFound -> CredentialQuotaError.ServerUnsupported
    is RemoteFailure.RateLimited -> CredentialQuotaError.RateLimited
    is RemoteFailure.InvalidResponse -> CredentialQuotaError.InvalidResponse
    is RemoteFailure.Network, is RemoteFailure.Timeout, is RemoteFailure.Tls -> CredentialQuotaError.Network
    else -> CredentialQuotaError.Server
}
