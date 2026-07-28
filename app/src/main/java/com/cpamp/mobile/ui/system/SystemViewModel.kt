package com.cpamp.mobile.ui.system

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cpamp.mobile.common.runSuspendCatching
import com.cpamp.mobile.data.auth.SessionRepository
import com.cpamp.mobile.data.remote.model.ManagerInfoDto
import com.cpamp.mobile.data.remote.model.ManagerStatusDto
import com.cpamp.mobile.data.system.SystemRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SystemTab { Status, Logs, Servers }

data class SystemUiState(
    val tab: SystemTab = SystemTab.Status,
    val info: ManagerInfoDto? = null,
    val status: ManagerStatusDto? = null,
    val logs: List<String> = emptyList(),
    val nextCursor: String? = null,
    val logFilter: String = "",
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val mutating: Boolean = false,
    val error: String? = null,
    val message: String? = null,
) {
    val visibleLogs: List<String>
        get() = logFilter.trim().takeIf(String::isNotEmpty)?.let { query ->
            logs.filter { it.contains(query, ignoreCase = true) }
        } ?: logs
}

@HiltViewModel
class SystemViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val repository: SystemRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SystemUiState())
    val state: StateFlow<SystemUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionRepository.session.collectLatest { session ->
                if (session == null) {
                    mutableState.value = SystemUiState()
                    return@collectLatest
                }
                mutableState.value = SystemUiState(loading = true)
                runSuspendCatching { repository.status(session) }
                    .onSuccess { snapshot ->
                        if (sessionRepository.session.value?.profile?.id == session.profile.id) {
                            mutableState.update {
                                it.copy(
                                    info = snapshot.info,
                                    status = snapshot.status,
                                    loading = false,
                                )
                            }
                        }
                    }
                    .onFailure {
                        if (sessionRepository.session.value?.profile?.id == session.profile.id) {
                            mutableState.update {
                                it.copy(
                                    loading = false,
                                    error = "SYSTEM_REQUEST_FAILED",
                                )
                            }
                        }
                    }
            }
        }
    }

    fun selectTab(tab: SystemTab) {
        mutableState.update { it.copy(tab = tab) }
    }

    fun setLogFilter(value: String) {
        mutableState.update { it.copy(logFilter = value.take(160)) }
    }

    fun refresh() {
        if (mutableState.value.loading || mutableState.value.loadingMore || mutableState.value.mutating) return
        viewModelScope.launch {
            val session = sessionRepository.session.value ?: return@launch
            mutableState.update { it.copy(loading = true, error = null, message = null) }
            when (mutableState.value.tab) {
                SystemTab.Status -> runSuspendCatching { repository.status(session) }
                    .onSuccess { snapshot ->
                        mutableState.update {
                            it.copy(
                                info = snapshot.info,
                                status = snapshot.status,
                                loading = false,
                            )
                        }
                    }
                    .onFailure {
                        mutableState.update { it.copy(loading = false, error = "SYSTEM_REQUEST_FAILED") }
                    }
                SystemTab.Logs -> runSuspendCatching { repository.logs(session) }
                    .onSuccess { page ->
                        mutableState.update {
                            it.copy(
                                logs = page.lines,
                                nextCursor = page.nextCursor,
                                loading = false,
                            )
                        }
                    }
                    .onFailure {
                        mutableState.update { it.copy(loading = false, error = "SYSTEM_REQUEST_FAILED") }
                    }
                SystemTab.Servers -> mutableState.update { it.copy(loading = false) }
            }
        }
    }

    fun loadMoreLogs() {
        val cursor = mutableState.value.nextCursor ?: return
        if (mutableState.value.loadingMore) return
        viewModelScope.launch {
            val session = sessionRepository.session.value ?: return@launch
            mutableState.update { it.copy(loadingMore = true, error = null) }
            runSuspendCatching { repository.logs(session, cursor) }
                .onSuccess { page ->
                    mutableState.update { state ->
                        state.copy(
                            logs = (state.logs + page.lines).distinct(),
                            nextCursor = page.nextCursor,
                        )
                    }
                }
                .onFailure { mutableState.update { it.copy(error = "SYSTEM_REQUEST_FAILED") } }
            mutableState.update { it.copy(loadingMore = false) }
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            val session = sessionRepository.session.value ?: return@launch
            mutableState.update { it.copy(mutating = true, error = null, message = null) }
            runSuspendCatching { repository.clearLogs(session) }
                .onSuccess {
                    mutableState.update {
                        it.copy(
                            logs = emptyList(),
                            nextCursor = null,
                            message = "LOGS_CLEARED",
                        )
                    }
                }
                .onFailure { mutableState.update { it.copy(error = "SYSTEM_REQUEST_FAILED") } }
            mutableState.update { it.copy(mutating = false) }
        }
    }

    fun clearNotice() {
        mutableState.update { it.copy(error = null, message = null) }
    }
}
