package com.cpamp.mobile

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.cpamp.mobile.ui.CPAMPMobileApp
import com.cpamp.mobile.ui.security.AppLockScreen
import com.cpamp.mobile.ui.security.AppLockViewModel
import com.cpamp.mobile.ui.settings.AppearanceViewModel
import com.cpamp.mobile.ui.theme.CPAMPMobileTheme
import com.cpamp.mobile.data.settings.AppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent {
            val appearanceViewModel: AppearanceViewModel = hiltViewModel()
            val appearance by appearanceViewModel.state.collectAsState()
            val settings = appearance.settings
            LaunchedEffect(settings.language) {
                val locales = LocaleListCompat.forLanguageTags(settings.language.languageTag)
                if (AppCompatDelegate.getApplicationLocales() != locales) {
                    AppCompatDelegate.setApplicationLocales(locales)
                }
            }
            CPAMPMobileTheme(
                darkThemeOverride = when (settings.theme) {
                    AppTheme.System -> null
                    AppTheme.Light -> false
                    AppTheme.Dark -> true
                },
                dynamicColor = settings.dynamicColor,
            ) {
                SecureAppRoot(appearanceViewModel)
            }
        }
    }

    @Composable
    private fun SecureAppRoot(
        appearanceViewModel: AppearanceViewModel,
        viewModel: AppLockViewModel = hiltViewModel(),
    ) {
        val state by viewModel.state.collectAsState()
        val appearance by appearanceViewModel.state.collectAsState()
        DisposableEffect(viewModel) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> viewModel.onForeground()
                    Lifecycle.Event.ON_STOP -> viewModel.onBackground()
                    else -> Unit
                }
            }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }

        when {
            state.loading || state.locked -> AppLockScreen(
                loading = state.loading,
                error = state.error,
                onUnlock = {
                    authenticate(
                        title = getString(R.string.unlock_prompt_title),
                        onSuccess = viewModel::unlock,
                        onFailure = viewModel::authenticationFailed,
                    )
                },
            )
            else -> CPAMPMobileApp(
                appLockState = state,
                onSetAppLockEnabled = { enabled ->
                    authenticate(
                        title = getString(
                            if (enabled) R.string.enable_app_lock_prompt else R.string.disable_app_lock_prompt,
                        ),
                        onSuccess = { viewModel.setEnabledAfterAuthentication(enabled) },
                        onFailure = viewModel::authenticationFailed,
                    )
                },
                onSetAppLockTimeout = viewModel::setTimeoutMinutes,
                appearanceState = appearance,
                onSetTheme = appearanceViewModel::setTheme,
                onSetLanguage = appearanceViewModel::setLanguage,
                onSetDynamicColor = appearanceViewModel::setDynamicColor,
            )
        }
    }

    private fun authenticate(title: String, onSuccess: () -> Unit, onFailure: () -> Unit) {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        onFailure()
                    }
                }

                override fun onAuthenticationFailed() {
                    onFailure()
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(getString(R.string.biometric_prompt_subtitle))
                .setAllowedAuthenticators(authenticators)
                .build(),
        )
    }
}
