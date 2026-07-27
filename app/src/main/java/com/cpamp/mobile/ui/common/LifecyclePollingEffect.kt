package com.cpamp.mobile.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun LifecyclePollingEffect(onActiveChanged: (Boolean) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, onActiveChanged) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> onActiveChanged(true)
                Lifecycle.Event.ON_STOP -> onActiveChanged(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            onActiveChanged(true)
        }
        onDispose {
            onActiveChanged(false)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

