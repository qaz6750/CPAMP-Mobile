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
    val dynamicColor: Boolean = true,
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
            dynamicColor = preferences[DYNAMIC_COLOR] ?: true,
        )
    }

    suspend fun setTheme(theme: AppTheme) {
        context.appearanceDataStore.edit { it[THEME] = theme.name }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.appearanceDataStore.edit { it[LANGUAGE] = language.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.appearanceDataStore.edit { it[DYNAMIC_COLOR] = enabled }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val LANGUAGE = stringPreferencesKey("language")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    }
}