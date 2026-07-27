package com.cpamp.mobile.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cpamp.mobile.data.update.AppUpdateRepository
import com.cpamp.mobile.data.update.AppUpdateState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val repository: AppUpdateRepository,
) : ViewModel() {
    val state: StateFlow<AppUpdateState> = repository.state

    fun checkForUpdates() {
        viewModelScope.launch { repository.checkForUpdates() }
    }

    fun downloadUpdate() {
        viewModelScope.launch { repository.downloadUpdate() }
    }

    fun refreshDownloadStatus() {
        viewModelScope.launch { repository.refreshDownloadStatus() }
    }
}