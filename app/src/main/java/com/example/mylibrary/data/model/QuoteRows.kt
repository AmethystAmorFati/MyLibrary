package com.example.mylibrary.data.model

import androidx.room.ColumnInfo

data class QuoteListRow(
    val id: Long,
    @ColumnInfo(name = "item_id")
    val itemId: Long,
    val content: String,
    val chapter: String?,
    val page: String?,
    @ColumnInfo(name = "created_time")
    val createdTime: Long,
    @ColumnInfo(name = "item_title")
    val itemTitle: String,
    val creator: String
)

data class QuoteStatisticsRow(
    @ColumnInfo(name = "reading_work_count")
    val readingWorkCount: Int,
    @ColumnInfo(name = "viewing_work_count")
    val viewingWorkCount: Int,
    @ColumnInfo(name = "quote_count")
    val quoteCount: Int,
    @ColumnInfo(name = "tag_count")
    val tagCount: Int
)

data class MediaItemStatisticsRow(
    @ColumnInfo(name = "item_id")
    val itemId: Long,
    @ColumnInfo(name = "type_id")
    val typeId: Long,
    @ColumnInfo(name = "item_title")
    val itemTitle: String,
    @ColumnInfo(name = "record_count")
    val recordCount: Long,
    @ColumnInfo(name = "quote_count")
    val quoteCount: Long,
    @ColumnInfo(name = "valued_record_count")
    val valuedRecordCount: Long,
    @ColumnInfo(name = "total_duration_minutes")
    val totalDurationMinutes: Long?,
    @ColumnInfo(name = "maximum_single_duration_minutes")
    val maximumSingleDurationMinutes: Long?
)

data class QuoteTagStatisticRow(
    val name: String,
    @ColumnInfo(name = "usage_count")
    val usageCount: Int
)
