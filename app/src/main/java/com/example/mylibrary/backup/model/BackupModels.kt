package com.example.mylibrary.backup.model

import com.example.mylibrary.domain.model.CoverStorageLimits

const val CURRENT_BACKUP_SCHEMA_VERSION = 5
const val BACKUP_FORMAT = "mylibrary-backup"
const val BACKUP_ROOT = "MyLibraryBackup/"

data class BackupFileInfo(
    val path: String,
    val size: Long,
    val sha256: String
)

data class BackupManifest(
    val format: String,
    val backupSchemaVersion: Int,
    val createdAt: String,
    val appVersionName: String,
    val appVersionCode: Long,
    val databaseVersion: Int,
    val counts: Map<String, Long>,
    val files: List<BackupFileInfo>,
    val missingCoverCount: Int = 0
)

data class BackupPreferences(
    val useGridLayout: Boolean = true,
    val libraryViewMode: String = "shelf",
    val gridColumns: Int = 4,
    val coverColumns: Int = 4,
    val timelineShowCreator: Boolean = false,
    val timelineShowRating: Boolean = false,
    val timelineShowStatus: Boolean = false,
    val timelineShowDuration: Boolean = true,
    val libraryShowTotalDuration: Boolean = true,
    val showQuoteChapter: Boolean = true,
    val showQuotePage: Boolean = true,
    val listDisplayFields: Set<String> = setOf("creator"),
    val currentThemeId: String? = null
)

data class BackupData(
    val itemTypes: List<BackupItemType>,
    val statuses: List<BackupStatus>,
    val fieldDefinitions: List<BackupFieldDefinition>,
    val tags: List<BackupTag>,
    val items: List<BackupItem>,
    val records: List<BackupRecord>,
    val activities: List<BackupActivity>,
    val itemTags: List<BackupItemTag>,
    val fieldValues: List<BackupFieldValue>,
    val quotes: List<BackupQuote>,
    val recordFieldValues: List<BackupRecordFieldValue> = emptyList()
) {
    fun counts(includedCoverCount: Int): Map<String, Long> = linkedMapOf(
        "itemTypes" to itemTypes.size.toLong(),
        "statuses" to statuses.size.toLong(),
        "fieldDefinitions" to fieldDefinitions.size.toLong(),
        "tags" to tags.size.toLong(),
        "items" to items.size.toLong(),
        "records" to records.size.toLong(),
        "activities" to activities.size.toLong(),
        "itemTags" to itemTags.size.toLong(),
        "fieldValues" to fieldValues.size.toLong(),
        "recordFieldValues" to recordFieldValues.size.toLong(),
        "quotes" to quotes.size.toLong(),
        "covers" to includedCoverCount.toLong()
    )
}

data class BackupItemType(
    val id: Long,
    val name: String,
    val sortOrder: Int
)

data class BackupStatus(
    val id: Long,
    val name: String,
    val sortOrder: Int,
    val enabled: Boolean,
    val scope: String = "item"
)

data class BackupFieldDefinition(
    val id: Long,
    val typeId: Long,
    val name: String,
    val dataType: String,
    val enabled: Boolean,
    val sortOrder: Int,
    val isFixed: Boolean,
    val options: List<String>,
    val optionDefinitions: List<BackupFieldOption> = options.mapIndexed { index, name ->
        BackupFieldOption(
            id = index + 1L,
            name = name,
            isActive = true,
            sortOrder = index
        )
    },
    val scope: String = "item",
    val unit: String? = null,
    val aggregations: Set<String> = emptySet()
)

data class BackupFieldOption(
    val id: Long,
    val name: String,
    val isActive: Boolean,
    val sortOrder: Int
)

data class BackupTag(
    val id: Long,
    val name: String,
    val parentId: Long?,
    val sortOrder: Int,
    val enabled: Boolean
)

data class BackupItem(
    val id: Long,
    val typeId: Long,
    val title: String,
    val coverRef: String?,
    val currentStatusId: Long?,
    val createdTime: Long,
    val updatedTime: Long,
    val deletedAt: Long?
)

data class BackupRecord(
    val id: Long,
    val itemId: Long,
    val startDate: Long,
    val endDate: Long?,
    val ratingHalfStars: Int?,
    val review: String?,
    val createdAt: Long,
    val statusSnapshot: String? = null,
    val durationMinutes: Long? = null
)

data class BackupActivity(
    val id: Long,
    val date: Long,
    val itemId: Long,
    val recordId: Long?
)

data class BackupItemTag(
    val itemId: Long,
    val tagId: Long
)

data class BackupFieldValue(
    val id: Long,
    val itemId: Long,
    val fieldId: Long,
    val value: String
)

data class BackupRecordFieldValue(
    val id: Long,
    val recordId: Long,
    val fieldId: Long,
    val value: String
)

data class BackupQuote(
    val id: Long,
    val itemId: Long,
    val content: String,
    val source: String?,
    val page: String?,
    val createdTime: Long,
    val chapter: String? = null
)

data class ImportPreview(
    val createdAt: String,
    val itemCount: Long,
    val quoteCount: Long
)

sealed interface BackupResult {
    data class Success(
        val warnings: List<BackupWarning> = emptyList()
    ) : BackupResult

    data class Failure(
        val reason: BackupFailureReason,
        val cause: Throwable? = null,
        val importStage: ImportStage? = null,
        val recovery: ImportRecoveryReport? = null
    ) : BackupResult

    data object Cancelled : BackupResult
}

sealed interface BackupPreparationResult {
    data class Ready(val preview: ImportPreview) : BackupPreparationResult
    data class Failure(
        val reason: BackupFailureReason,
        val cause: Throwable? = null
    ) : BackupPreparationResult
}

sealed interface BackupWarning {
    data class MissingCovers(val count: Int) : BackupWarning
    data object OldCoverCleanupFailed : BackupWarning
    data object StagingCleanupFailed : BackupWarning
    data class SkippedThemes(val count: Int) : BackupWarning
    data object CurrentThemeUnavailable : BackupWarning
    data object ThemeRestoreFailed : BackupWarning
}

enum class ImportStage {
    READ_CURRENT_STATE,
    STAGE_COVERS,
    WRITE_PREFERENCES,
    REPLACE_DATABASE,
    CLEANUP_OLD_COVERS,
    INSTALL_THEMES,
    CLEANUP_STAGING
}

enum class RecoveryState {
    NOT_REQUIRED,
    PRESERVED,
    RESTORED,
    FAILED
}

data class ImportRecoveryReport(
    val database: RecoveryState,
    val preferences: RecoveryState,
    val covers: RecoveryState,
    val staging: RecoveryState,
    val requiresRecentBackup: Boolean
) {
    val fullyRecovered: Boolean
        get() = !requiresRecentBackup &&
            listOf(database, preferences, covers, staging).none {
                it == RecoveryState.FAILED
            }
}

enum class BackupFailureReason {
    INVALID_ARCHIVE,
    UNSUPPORTED_NEWER_VERSION,
    IO_ERROR,
    INVALID_DATA,
    DATABASE_ERROR
}

object BackupArchiveLimits {
    const val MAX_ENTRY_COUNT = 2_000
    const val MAX_MANIFEST_BYTES = 1L * 1024 * 1024
    const val MAX_DATA_JSON_BYTES = 64L * 1024 * 1024
    const val MAX_PREFERENCES_BYTES = 1L * 1024 * 1024
    const val MAX_COVER_BYTES = CoverStorageLimits.MAX_SOURCE_BYTES

    /**
     * Per-file extraction limit applied to every entry under `themes/`.
     *
     * This value (32 MiB) is the maximum legal single-file size across all
     * theme resource types defined in [com.example.mylibrary.ui.theme.ThemeResourceLimits]:
     * - Background image: 12 MiB (`MAX_BACKGROUND_IMAGE_FILE_BYTES`)
     * - Card image: 8 MiB (`MAX_CARD_IMAGE_FILE_BYTES`)
     * - Dialog image: 8 MiB (`MAX_DIALOG_IMAGE_FILE_BYTES`)
     * - Navigation icon: 512 KiB (`MAX_NAVIGATION_IMAGE_FILE_BYTES`)
     * - Single font: 32 MiB (`MAX_SINGLE_FONT_FILE_BYTES`)
     *
     * No legal theme resource file can exceed 32 MiB, so this limit acts as
     * a safe extraction guard without rejecting any installable theme.
     * [com.example.mylibrary.ui.theme.importer.ThemeInstaller] still
     * performs the final per-type classification and size enforcement
     * during restore, so backup validation never needs to duplicate the
     * type-specific rules.
     */
    const val MAX_THEME_FILE_BYTES = 32L * 1024 * 1024
    const val MAX_ARCHIVE_BYTES = 512L * 1024 * 1024
    const val MAX_TOTAL_EXTRACTED_BYTES = 512L * 1024 * 1024
}
