package com.cpamp.mobile.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cpamp.mobile.data.cache.CacheCleanupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class CacheCleanupResult { Success, Failure }

data class CacheCleanupUiState(
    val clearing: Boolean = false,
    val result: CacheCleanupResult? = null,
)

@HiltViewModel
class CacheCleanupViewModel @Inject constructor(
    private val repository: CacheCleanupRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CacheCleanupUiState())
    val state: StateFlow<CacheCleanupUiState> = mutableState.asStateFlow()

    fun clear() {
        if (mutableState.value.clearing) return
        viewModelScope.launch {
            mutableState.value = CacheCleanupUiState(clearing = true)
            mutableState.value = runCatching { repository.clearRegenerableCache() }
                .fold(
                    onSuccess = { CacheCleanupUiState(result = CacheCleanupResult.Success) },
                    onFailure = { CacheCleanupUiState(result = CacheCleanupResult.Failure) },
                )
        }
    }

    fun clearResult() {
        mutableState.value = mutableState.value.copy(result = null)
    }
}
