package com.cpamp.mobile.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cpamp.mobile.common.runSuspendCatching
import com.cpamp.mobile.data.auth.ConnectionException
import com.cpamp.mobile.data.auth.SessionException
import com.cpamp.mobile.data.auth.SessionRepository
import com.cpamp.mobile.data.cache.CacheCleanupRepository
import com.cpamp.mobile.domain.model.AuthenticatedSession
import com.cpamp.mobile.domain.model.ServerProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SessionUiState(
    val initializing: Boolean = true,
    val submitting: Boolean = false,
    val session: AuthenticatedSession? = null,
    val profiles: List<ServerProfile> = emptyList(),
    val error: AuthUiError? = null,
)

enum class AuthUiError {
    InvalidAddress,
    MissingKey,
    Unauthorized,
    LightMode,
    NotManager,
    Redirect,
    Tls,
    Timeout,
    Network,
    Server,
    SavedKeyUnavailable,
    Unknown,
}

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val repository: SessionRepository,
    private val cacheCleanupRepository: CacheCleanupRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SessionUiState())
    val state: StateFlow<SessionUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            runSuspendCatching { cacheCleanupRepository.clearLegacyResponseCaches() }
        }
        viewModelScope.launch {
            repository.profiles.collectLatest { stored ->
                mutableState.update { it.copy(profiles = stored.profiles) }
            }
        }
        viewModelScope.launch {
            val restored = repository.restore()
            mutableState.update { state ->
                state.copy(
                    initializing = false,
                    session = restored.getOrNull(),
                    error = restored.exceptionOrNull()
                        ?.takeUnless {
                            it is SessionException && it.reason == SessionException.Reason.NoActiveProfile
                        }
                        ?.toAuthUiError(),
                )
            }
        }
    }

    fun login(name: String, address: String, adminKey: String, allowCleartext: Boolean) {
        launchSessionRequest {
            repository.login(name, address, adminKey, allowCleartext)
        }
    }

    fun switchTo(profileId: String) {
        launchSessionRequest(clearCurrentSession = true) { repository.switchTo(profileId) }
    }

    fun delete(profileId: String) {
        viewModelScope.launch {
            mutableState.update { it.copy(submitting = true, error = null) }
            val result = runSuspendCatching { repository.delete(profileId) }
            mutableState.update { state ->
                state.copy(
                    submitting = false,
                    session = repository.session.value,
                    error = result.exceptionOrNull()?.toAuthUiError(),
                )
            }
        }
    }

    fun disconnect() {
        repository.disconnect()
        mutableState.update { it.copy(session = null, error = null) }
    }

    fun clearError() {
        mutableState.update { it.copy(error = null) }
    }

    private fun launchSessionRequest(
        clearCurrentSession: Boolean = false,
        request: suspend () -> AuthenticatedSession,
    ) {
        viewModelScope.launch {
            mutableState.update { state ->
                state.copy(
                    submitting = true,
                    session = if (clearCurrentSession) null else state.session,
                    error = null,
                )
            }
            runSuspendCatching { request() }
                .onSuccess { session ->
                    mutableState.update { it.copy(submitting = false, session = session) }
                }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(
                            submitting = false,
                            error = error.toAuthUiError(),
                        )
                    }
                }
        }
    }
}

private fun Throwable.toAuthUiError(): AuthUiError = when {
    this is ConnectionException -> when (reason) {
        ConnectionException.Reason.Unauthorized -> AuthUiError.Unauthorized
        ConnectionException.Reason.UnsupportedLightMode -> AuthUiError.LightMode
        ConnectionException.Reason.NotManagerServer -> AuthUiError.NotManager
        ConnectionException.Reason.RedirectBlocked -> AuthUiError.Redirect
        ConnectionException.Reason.Tls -> AuthUiError.Tls
        ConnectionException.Reason.Timeout -> AuthUiError.Timeout
        ConnectionException.Reason.Network -> AuthUiError.Network
        ConnectionException.Reason.Server, ConnectionException.Reason.InvalidResponse -> AuthUiError.Server
    }
    this is SessionException -> when (reason) {
        SessionException.Reason.MissingAdminKey -> AuthUiError.MissingKey
        SessionException.Reason.SavedKeyUnavailable -> AuthUiError.SavedKeyUnavailable
        SessionException.Reason.CleartextConfirmationRequired -> AuthUiError.InvalidAddress
        SessionException.Reason.NoActiveProfile,
        SessionException.Reason.ProfileNotFound,
        -> AuthUiError.Unknown
    }
    this is IllegalArgumentException -> AuthUiError.InvalidAddress
    else -> AuthUiError.Unknown
}
