package com.cpamp.mobile.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appearanceDataStore by preferencesDataStore(name = "appearance_settings")

enum class AppTheme { System, Light, Dark }
enum class AppLanguage(val languageTag: String) {
    System(""),
    SimplifiedChinese("zh-CN"),
    English("en"),
}

data class AppearanceSettings(
    val theme: AppTheme = AppTheme.System,
    val language: AppLanguage = AppLanguage.System,
    val allowScreenshots: Boolean = true,
    val hideAddresses: Boolean = false,
)

@Singleton
class AppearanceRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val settings: Flow<AppearanceSettings> = context.appearanceDataStore.data.map { preferences ->
        AppearanceSettings(
            theme = preferences[THEME]?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() } ?: AppTheme.System,
            language = preferences[LANGUAGE]?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
                ?: AppLanguage.System,
            allowScreenshots = preferences[ALLOW_SCREENSHOTS] ?: true,
            hideAddresses = preferences[HIDE_ADDRESSES] ?: false,
        )
    }

    suspend fun setTheme(theme: AppTheme) {
        context.appearanceDataStore.edit { it[THEME] = theme.name }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.appearanceDataStore.edit { it[LANGUAGE] = language.name }
    }

    suspend fun setAllowScreenshots(enabled: Boolean) {
        context.appearanceDataStore.edit { it[ALLOW_SCREENSHOTS] = enabled }
    }

    suspend fun setHideAddresses(enabled: Boolean) {
        context.appearanceDataStore.edit { it[HIDE_ADDRESSES] = enabled }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val LANGUAGE = stringPreferencesKey("language")
        val ALLOW_SCREENSHOTS = booleanPreferencesKey("allow_screenshots")
        val HIDE_ADDRESSES = booleanPreferencesKey("hide_addresses")
    }
}