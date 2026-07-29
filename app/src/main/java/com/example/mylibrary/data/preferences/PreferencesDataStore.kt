package com.example.mylibrary.data.preferences

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

internal val Context.myLibraryPreferences by preferencesDataStore(
    name = "my_library_preferences"
)

