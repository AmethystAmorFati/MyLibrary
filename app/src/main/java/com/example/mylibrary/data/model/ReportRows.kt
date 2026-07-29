package com.example.mylibrary.data.model

import androidx.room.ColumnInfo

data class ReportRecordRow(
    @ColumnInfo(name = "record_id")
    val recordId: Long,
    @ColumnInfo(name = "item_id")
    val itemId: Long,
    @ColumnInfo(name = "start_date")
    val startDate: Long,
    @ColumnInfo(name = "end_date")
    val endDate: Long?,
    @ColumnInfo(name = "rating_half_stars")
    val ratingHalfStars: Int?,
    val review: String?,
    @ColumnInfo(name = "type_id")
    val typeId: Long,
    @ColumnInfo(name = "type_name")
    val typeName: String,
    @ColumnInfo(name = "type_sort_order")
    val typeSortOrder: Int,
    val title: String,
    @ColumnInfo(name = "cover_path")
    val coverPath: String?,
    @ColumnInfo(name = "current_status_id")
    val currentStatusId: Long?,
    @ColumnInfo(name = "current_status_name")
    val currentStatusName: String?,
    val creator: String?
)

data class ReportActivityRow(
    val date: Long,
    @ColumnInfo(name = "item_id")
    val itemId: Long,
    @ColumnInfo(name = "type_id")
    val typeId: Long
)

data class ReportItemTagRow(
    @ColumnInfo(name = "item_id")
    val itemId: Long,
    val name: String
)

data class ReportQuoteRow(
    @ColumnInfo(name = "quote_id")
    val quoteId: Long,
    @ColumnInfo(name = "item_id")
    val itemId: Long,
    @ColumnInfo(name = "item_title")
    val itemTitle: String,
    val content: String,
    val source: String?,
    val chapter: String?,
    val page: String?,
    @ColumnInfo(name = "created_time")
    val createdTime: Long
)
