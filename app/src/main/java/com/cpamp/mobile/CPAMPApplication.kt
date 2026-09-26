package com.cpamp.mobile

import android.app.Application
import com.cpamp.mobile.data.profile.ServerProfileStore
import com.cpamp.mobile.ui.widget.UsageWidget
import com.cpamp.mobile.ui.widget.UsageWidgetWork
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

@HiltAndroidApp
class CPAMPApplication : Application() {
    @Inject lateinit var profileStore: ServerProfileStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Sign-in, server switches and deletions change what the home-screen widget should show.
        profileStore.profiles
            .map { it.activeProfileId }
            .distinctUntilChanged()
            .drop(1)
            .onEach {
                UsageWidgetWork.requestRefresh(this)
                UsageWidget.reload(this)
            }
            .launchIn(scope)
    }
}
