package com.cpamp.mobile.ui.system

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cpamp.mobile.data.auth.SessionRepository
import com.cpamp.mobile.data.remote.model.ManagerInfoDto
import com.cpamp.mobile.data.remote.model.ManagerStatusDto
import com.cpamp.mobile.data.system.SystemRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SystemTab { Status, Logs, Servers, Security }

data class SystemUiState(
    val tab: SystemTab = SystemTab.Status,
    val info: ManagerInfoDto? = null,
    val status: ManagerStatusDto? = null,
    val logs: List<String> = emptyList(),
    val nextCursor: String? = null,
    val logFilter: String = "",
    val loading: Boolean = true,
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

    init { refresh() }

    fun selectTab(tab: SystemTab) {
        mutableState.value = mutableState.value.copy(tab = tab)
    }

    fun setLogFilter(value: String) {
        mutableState.value = mutableState.value.copy(logFilter = value.take(160))
    }

    fun refresh() {
        viewModelScope.launch {
            val session = sessionRepository.session.value ?: return@launch
            mutableState.value = mutableState.value.copy(loading = true, error = null, message = null)
            val snapshot = async { runCatching { repository.status(session) } }
            val logs = async { runCatching { repository.logs(session) } }
            val snapshotResult = snapshot.await()
            val logsResult = logs.await()
            mutableState.value = mutableState.value.copy(
                info = snapshotResult.getOrNull()?.info ?: mutableState.value.info,
                status = snapshotResult.getOrNull()?.status ?: mutableState.value.status,
                logs = logsResult.getOrNull()?.lines ?: mutableState.value.logs,
                nextCursor = logsResult.getOrNull()?.nextCursor,
                loading = false,
                error = listOfNotNull(snapshotResult.exceptionOrNull(), logsResult.exceptionOrNull())
                    .firstOrNull()?.let { "SYSTEM_REQUEST_FAILED" },
            )
        }
    }

    fun loadMoreLogs() {
        val cursor = mutableState.value.nextCursor ?: return
        if (mutableState.value.loadingMore) return
        viewModelScope.launch {
            val session = sessionRepository.session.value ?: return@launch
            mutableState.value = mutableState.value.copy(loadingMore = true, error = null)
            runCatching { repository.logs(session, cursor) }
                .onSuccess { page ->
                    mutableState.value = mutableState.value.copy(
                        logs = (mutableState.value.logs + page.lines).distinct(),
                        nextCursor = page.nextCursor,
                    )
                }
                .onFailure { mutableState.value = mutableState.value.copy(error = "SYSTEM_REQUEST_FAILED") }
            mutableState.value = mutableState.value.copy(loadingMore = false)
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            val session = sessionRepository.session.value ?: return@launch
            mutableState.value = mutableState.value.copy(mutating = true, error = null, message = null)
            runCatching { repository.clearLogs(session) }
                .onSuccess {
                    mutableState.value = mutableState.value.copy(
                        logs = emptyList(),
                        nextCursor = null,
                        message = "LOGS_CLEARED",
                    )
                }
                .onFailure { mutableState.value = mutableState.value.copy(error = "SYSTEM_REQUEST_FAILED") }
            mutableState.value = mutableState.value.copy(mutating = false)
        }
    }

    fun clearNotice() {
        mutableState.value = mutableState.value.copy(error = null, message = null)
    }
}