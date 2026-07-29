package com.example.mylibrary.data.database

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.mylibrary.domain.model.FieldDataType

object DefaultLibraryData {
    const val BOOK_TYPE_ID = 1L
    const val MOVIE_TYPE_ID = 2L
    const val AUTHOR_FIELD_ID = 1L
    const val DIRECTOR_FIELD_ID = 2L
    const val WANT_TO_WATCH_STATUS_ID = 1L
    const val IN_PROGRESS_STATUS_ID = 2L
    const val COMPLETED_STATUS_ID = 3L
    const val PAUSED_STATUS_ID = 4L
}

object DefaultLibraryDataCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)

        db.execSQL(
            "INSERT INTO item_types (id, name, sort_order) VALUES (?, ?, ?)",
            arrayOf<Any?>(DefaultLibraryData.BOOK_TYPE_ID, "Book", 0)
        )
        db.execSQL(
            "INSERT INTO item_types (id, name, sort_order) VALUES (?, ?, ?)",
            arrayOf<Any?>(DefaultLibraryData.MOVIE_TYPE_ID, "Movie", 1)
        )
        db.execSQL(
            "INSERT INTO field_definitions " +
                "(id, type_id, name, data_type, enabled, sort_order, is_fixed) " +
                "VALUES (?, ?, ?, ?, ?, ?, 1)",
            arrayOf<Any?>(
                DefaultLibraryData.AUTHOR_FIELD_ID,
                DefaultLibraryData.BOOK_TYPE_ID,
                "author",
                FieldDataType.TEXT.storageValue,
                1,
                0
            )
        )
        db.execSQL(
            "INSERT INTO field_definitions " +
                "(id, type_id, name, data_type, enabled, sort_order, is_fixed) " +
                "VALUES (?, ?, ?, ?, ?, ?, 1)",
            arrayOf<Any?>(
                DefaultLibraryData.DIRECTOR_FIELD_ID,
                DefaultLibraryData.MOVIE_TYPE_ID,
                "director",
                FieldDataType.TEXT.storageValue,
                1,
                0
            )
        )
        insertDefaultStatuses(db)
    }
}

internal fun insertDefaultStatuses(db: SupportSQLiteDatabase) {
    val statuses = listOf(
        DefaultLibraryData.WANT_TO_WATCH_STATUS_ID to "想看",
        DefaultLibraryData.IN_PROGRESS_STATUS_ID to "进行中",
        DefaultLibraryData.COMPLETED_STATUS_ID to "完成",
        DefaultLibraryData.PAUSED_STATUS_ID to "暂停"
    )
    statuses.forEachIndexed { sortOrder, (id, name) ->
        db.execSQL(
            "INSERT INTO statuses (id, name, sort_order, enabled) VALUES (?, ?, ?, 1)",
            arrayOf<Any?>(id, name, sortOrder)
        )
    }
}
