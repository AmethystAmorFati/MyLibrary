package com.example.mylibrary.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.edit
import com.example.mylibrary.backup.model.BackupPreferences
import com.example.mylibrary.data.preferences.myLibraryPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.example.mylibrary.domain.model.LibraryViewMode
import com.example.mylibrary.domain.model.LibraryViewPreferences

class UserPreferencesRepository(
    private val context: Context
) {
    val useGridLayout: Flow<Boolean> =
        context.myLibraryPreferences.data.map { preferences ->
            preferences[USE_GRID_LAYOUT] ?: true
        }

    suspend fun setUseGridLayout(enabled: Boolean) {
        context.myLibraryPreferences.edit { preferences ->
            preferences[USE_GRID_LAYOUT] = enabled
        }
    }

    val libraryViewPreferences: Flow<LibraryViewPreferences> =
        context.myLibraryPreferences.data.map { preferences ->
            LibraryViewPreferences(
                viewMode = LibraryViewMode.fromStorageValue(
                    preferences[LIBRARY_VIEW_MODE]
                ),
                gridColumns = preferences[GRID_COLUMNS]?.coerceIn(2, 6)
                    ?: LibraryViewPreferences().gridColumns,
                coverColumns = preferences[COVER_COLUMNS]?.coerceIn(2, 6)
                    ?: LibraryViewPreferences().coverColumns,
                timelineShowCreator = preferences[TIMELINE_SHOW_CREATOR]
                    ?: LibraryViewPreferences().timelineShowCreator,
                timelineShowRating = preferences[TIMELINE_SHOW_RATING]
                    ?: LibraryViewPreferences().timelineShowRating,
                timelineShowStatus = preferences[TIMELINE_SHOW_STATUS]
                    ?: LibraryViewPreferences().timelineShowStatus,
                timelineShowDuration = preferences[TIMELINE_SHOW_DURATION]
                    ?: LibraryViewPreferences().timelineShowDuration,
                libraryShowTotalDuration = preferences[LIBRARY_SHOW_TOTAL_DURATION]
                    ?: LibraryViewPreferences().libraryShowTotalDuration,
                showQuoteChapter = preferences[SHOW_QUOTE_CHAPTER]
                    ?: LibraryViewPreferences().showQuoteChapter,
                showQuotePage = preferences[SHOW_QUOTE_PAGE]
                    ?: LibraryViewPreferences().showQuotePage,
                listDisplayFields = preferences[LIST_DISPLAY_FIELDS]
                    ?: LibraryViewPreferences().listDisplayFields
            )
        }

    suspend fun setLibraryViewMode(mode: LibraryViewMode) {
        context.myLibraryPreferences.edit { preferences ->
            preferences[LIBRARY_VIEW_MODE] = mode.storageValue
        }
    }

    suspend fun setListDisplayFields(fields: Set<String>) {
        context.myLibraryPreferences.edit { preferences ->
            preferences[LIST_DISPLAY_FIELDS] = fields
        }
    }

    suspend fun setGridColumns(columns: Int) {
        require(columns in 2..6) { "网格列数必须在 2 到 6 之间" }
        context.myLibraryPreferences.edit { preferences ->
            preferences[GRID_COLUMNS] = columns
        }
    }

    suspend fun setCoverColumns(columns: Int) {
        require(columns in 2..6) { "纯图列数必须在 2 到 6 之间" }
        context.myLibraryPreferences.edit { preferences ->
            preferences[COVER_COLUMNS] = columns
        }
    }

    suspend fun setTimelineShowCreator(enabled: Boolean) {
        context.myLibraryPreferences.edit { preferences ->
            preferences[TIMELINE_SHOW_CREATOR] = enabled
        }
    }

    suspend fun setTimelineShowRating(enabled: Boolean) {
        context.myLibraryPreferences.edit { preferences ->
            preferences[TIMELINE_SHOW_RATING] = enabled
        }
    }

    suspend fun setTimelineShowStatus(enabled: Boolean) {
        context.myLibraryPreferences.edit { preferences ->
            preferences[TIMELINE_SHOW_STATUS] = enabled
        }
    }

    suspend fun setTimelineShowDuration(enabled: Boolean) {
        context.myLibraryPreferences.edit { preferences ->
            preferences[TIMELINE_SHOW_DURATION] = enabled
        }
    }

    suspend fun setLibraryShowTotalDuration(enabled: Boolean) {
        context.myLibraryPreferences.edit { preferences ->
            preferences[LIBRARY_SHOW_TOTAL_DURATION] = enabled
        }
    }

    suspend fun setShowQuoteChapter(enabled: Boolean) {
        context.myLibraryPreferences.edit { preferences ->
            preferences[SHOW_QUOTE_CHAPTER] = enabled
        }
    }

    suspend fun setShowQuotePage(enabled: Boolean) {
        context.myLibraryPreferences.edit { preferences ->
            preferences[SHOW_QUOTE_PAGE] = enabled
        }
    }

    suspend fun snapshotForBackup(): BackupPreferences {
        val preferences = context.myLibraryPreferences.data.first()
        val defaults = LibraryViewPreferences()
        return BackupPreferences(
            useGridLayout = preferences[USE_GRID_LAYOUT] ?: true,
            libraryViewMode = LibraryViewMode.fromStorageValue(
                preferences[LIBRARY_VIEW_MODE]
            ).storageValue,
            gridColumns = preferences[GRID_COLUMNS]?.coerceIn(2, 6)
                ?: defaults.gridColumns,
            coverColumns = preferences[COVER_COLUMNS]?.coerceIn(2, 6)
                ?: defaults.coverColumns,
            timelineShowCreator = preferences[TIMELINE_SHOW_CREATOR]
                ?: defaults.timelineShowCreator,
            timelineShowRating = preferences[TIMELINE_SHOW_RATING]
                ?: defaults.timelineShowRating,
            timelineShowStatus = preferences[TIMELINE_SHOW_STATUS]
                ?: defaults.timelineShowStatus,
            timelineShowDuration = preferences[TIMELINE_SHOW_DURATION]
                ?: defaults.timelineShowDuration,
            libraryShowTotalDuration = preferences[LIBRARY_SHOW_TOTAL_DURATION]
                ?: defaults.libraryShowTotalDuration,
            showQuoteChapter = preferences[SHOW_QUOTE_CHAPTER]
                ?: defaults.showQuoteChapter,
            showQuotePage = preferences[SHOW_QUOTE_PAGE]
                ?: defaults.showQuotePage,
            listDisplayFields = preferences[LIST_DISPLAY_FIELDS]
                ?: defaults.listDisplayFields
        )
    }

    suspend fun replaceFromBackup(snapshot: BackupPreferences) {
        context.myLibraryPreferences.edit { preferences ->
            preferences.clear()
            preferences[USE_GRID_LAYOUT] = snapshot.useGridLayout
            preferences[LIBRARY_VIEW_MODE] = LibraryViewMode.fromStorageValue(
                snapshot.libraryViewMode
            ).storageValue
            preferences[GRID_COLUMNS] = snapshot.gridColumns.coerceIn(2, 6)
            preferences[COVER_COLUMNS] = snapshot.coverColumns.coerceIn(2, 6)
            preferences[TIMELINE_SHOW_CREATOR] = snapshot.timelineShowCreator
            preferences[TIMELINE_SHOW_RATING] = snapshot.timelineShowRating
            preferences[TIMELINE_SHOW_STATUS] = snapshot.timelineShowStatus
            preferences[TIMELINE_SHOW_DURATION] = snapshot.timelineShowDuration
            preferences[LIBRARY_SHOW_TOTAL_DURATION] = snapshot.libraryShowTotalDuration
            preferences[SHOW_QUOTE_CHAPTER] = snapshot.showQuoteChapter
            preferences[SHOW_QUOTE_PAGE] = snapshot.showQuotePage
            preferences[LIST_DISPLAY_FIELDS] = snapshot.listDisplayFields
        }
    }

    private companion object {
        val USE_GRID_LAYOUT = booleanPreferencesKey("use_grid_layout")
        val LIBRARY_VIEW_MODE = stringPreferencesKey("library_view_mode")
        val GRID_COLUMNS = intPreferencesKey("library_grid_columns")
        val COVER_COLUMNS = intPreferencesKey("library_cover_columns")
        val TIMELINE_SHOW_CREATOR = booleanPreferencesKey("timeline_show_creator")
        val TIMELINE_SHOW_RATING = booleanPreferencesKey("timeline_show_rating")
        val TIMELINE_SHOW_STATUS = booleanPreferencesKey("timeline_show_status")
        val TIMELINE_SHOW_DURATION = booleanPreferencesKey("timeline_show_duration")
        val LIBRARY_SHOW_TOTAL_DURATION =
            booleanPreferencesKey("library_show_total_duration")
        val SHOW_QUOTE_CHAPTER = booleanPreferencesKey("show_quote_chapter")
        val SHOW_QUOTE_PAGE = booleanPreferencesKey("show_quote_page")
        val LIST_DISPLAY_FIELDS = stringSetPreferencesKey("list_display_fields")
    }
}
