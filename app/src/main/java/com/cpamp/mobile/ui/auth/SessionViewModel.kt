package com.cpamp.mobile.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cpamp.mobile.data.auth.ConnectionException
import com.cpamp.mobile.data.auth.SessionRepository
import com.cpamp.mobile.domain.model.AuthenticatedSession
import com.cpamp.mobile.domain.model.ServerProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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
) : ViewModel() {
    private val mutableState = MutableStateFlow(SessionUiState())
    val state: StateFlow<SessionUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.profiles.collectLatest { stored ->
                mutableState.value = mutableState.value.copy(profiles = stored.profiles)
            }
        }
        viewModelScope.launch {
            val restored = repository.restore()
            mutableState.value = mutableState.value.copy(
                initializing = false,
                session = restored.getOrNull(),
                error = restored.exceptionOrNull()
                    ?.takeUnless { it.message == "NO_ACTIVE_PROFILE" }
                    ?.toAuthUiError(),
            )
        }
    }

    fun login(name: String, address: String, adminKey: String, allowCleartext: Boolean) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(submitting = true, error = null)
            runCatching { repository.login(name, address, adminKey, allowCleartext) }
                .onSuccess { session ->
                    mutableState.value = mutableState.value.copy(submitting = false, session = session)
                }
                .onFailure { error ->
                    mutableState.value = mutableState.value.copy(
                        submitting = false,
                        error = error.toAuthUiError(),
                    )
                }
        }
    }

    fun switchTo(profileId: String) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(submitting = true, session = null, error = null)
            runCatching { repository.switchTo(profileId) }
                .onSuccess { mutableState.value = mutableState.value.copy(submitting = false, session = it) }
                .onFailure {
                    mutableState.value = mutableState.value.copy(
                        submitting = false,
                        error = it.toAuthUiError(),
                    )
                }
        }
    }

    fun delete(profileId: String) {
        viewModelScope.launch {
            repository.delete(profileId)
            mutableState.value = mutableState.value.copy(
                session = repository.session.value,
                error = null,
            )
        }
    }

    fun disconnect() {
        repository.disconnect()
        mutableState.value = mutableState.value.copy(session = null, error = null)
    }

    fun clearError() {
        mutableState.value = mutableState.value.copy(error = null)
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
    message == "ADMIN_KEY_REQUIRED" -> AuthUiError.MissingKey
    message == "SAVED_KEY_UNAVAILABLE" -> AuthUiError.SavedKeyUnavailable
    this is IllegalArgumentException -> AuthUiError.InvalidAddress
    else -> AuthUiError.Unknown
}

