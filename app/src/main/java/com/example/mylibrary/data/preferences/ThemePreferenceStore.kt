package com.example.mylibrary.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

interface ThemePreferenceStore {
    suspend fun readCurrentThemeId(): String?

    suspend fun setCurrentThemeId(themeId: String)

    suspend fun clearCurrentThemeId()
}

class DataStoreThemePreferenceStore(
    context: Context
) : ThemePreferenceStore {
    private val applicationContext = context.applicationContext

    override suspend fun readCurrentThemeId(): String? =
        applicationContext.myLibraryPreferences.data.first()[CURRENT_THEME_ID]

    override suspend fun setCurrentThemeId(themeId: String) {
        applicationContext.myLibraryPreferences.edit { preferences ->
            preferences[CURRENT_THEME_ID] = themeId
        }
    }

    override suspend fun clearCurrentThemeId() {
        applicationContext.myLibraryPreferences.edit { preferences ->
            preferences.remove(CURRENT_THEME_ID)
        }
    }

    companion object {
        const val CURRENT_THEME_ID_KEY = "current_theme_id"

        private val CURRENT_THEME_ID =
            stringPreferencesKey(CURRENT_THEME_ID_KEY)
    }
}
