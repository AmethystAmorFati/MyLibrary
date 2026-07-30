package com.example.mylibrary.data.dao

import androidx.room.Dao
import androidx.room.Query
import com.example.mylibrary.data.entity.FieldDefinitionEntity
import com.example.mylibrary.data.entity.FieldValueEntity
import com.example.mylibrary.data.entity.ItemTypeEntity
import com.example.mylibrary.data.model.ReportActivityRow
import com.example.mylibrary.data.model.ReportItemTagRow
import com.example.mylibrary.data.model.ReportQuoteRow
import com.example.mylibrary.data.model.ReportRecordRow

@Dao
interface ReportDao {
    @Query("SELECT * FROM item_types ORDER BY sort_order, id")
    suspend fun getItemTypes(): List<ItemTypeEntity>

    @Query(
        """
        SELECT definition.*
        FROM field_definitions definition
        JOIN item_types item_type ON item_type.id = definition.type_id
        ORDER BY item_type.sort_order, definition.sort_order, definition.id
        """
    )
    suspend fun getFieldDefinitions(): List<FieldDefinitionEntity>

    @Query(
        """
        SELECT
            record.id AS record_id,
            record.item_id,
            record.start_date,
            record.duration_minutes,
            record.created_at AS record_created_at,
            item.type_id,
            item_type.name AS type_name,
            item_type.sort_order AS type_sort_order,
            item.title,
            item.cover_path,
            item.current_status_id,
            current_status.name AS current_status_name,
            current_status.sort_order AS current_status_sort_order,
            (
                SELECT creator_value.value
                FROM field_values creator_value
                JOIN field_definitions creator_field
                  ON creator_field.id = creator_value.field_id
                WHERE creator_value.item_id = item.id
                  AND creator_field.name IN ('author', 'director')
                  AND creator_field.enabled = 1
                ORDER BY creator_field.sort_order, creator_field.id
                LIMIT 1
            ) AS creator
        FROM records record
        JOIN items item ON item.id = record.item_id
        JOIN item_types item_type ON item_type.id = item.type_id
        LEFT JOIN statuses current_status ON current_status.id = item.current_status_id
        WHERE record.start_date >= :startInclusive
          AND record.start_date < :endExclusive
          AND item.deleted_at IS NULL
          AND (:typeCount = 0 OR item.type_id IN (:typeIds))
        ORDER BY
            item_type.sort_order,
            item.title COLLATE NOCASE,
            item.id,
            record.start_date,
            record.id
        """
    )
    suspend fun getRecords(
        startInclusive: Long,
        endExclusive: Long,
        typeIds: List<Long>,
        typeCount: Int
    ): List<ReportRecordRow>

    @Query(
        """
        SELECT activity.date, activity.item_id, item.type_id
        FROM activities activity
        JOIN items item ON item.id = activity.item_id
        WHERE activity.date >= :startInclusive
          AND activity.date < :endExclusive
          AND item.deleted_at IS NULL
          AND (:typeCount = 0 OR item.type_id IN (:typeIds))
        ORDER BY activity.date, activity.item_id, activity.id
        """
    )
    suspend fun getActivities(
        startInclusive: Long,
        endExclusive: Long,
        typeIds: List<Long>,
        typeCount: Int
    ): List<ReportActivityRow>

    @Query(
        """
        SELECT field_value.*
        FROM field_values field_value
        WHERE field_value.item_id IN (:itemIds)
          AND field_value.field_id IN (:fieldIds)
        ORDER BY field_value.item_id, field_value.field_id
        """
    )
    suspend fun getItemFieldValues(
        itemIds: List<Long>,
        fieldIds: List<Long>
    ): List<FieldValueEntity>

    @Query(
        """
        SELECT
            item_tag.item_id,
            tag.id AS tag_id,
            tag.name,
            tag.sort_order
        FROM item_tags item_tag
        JOIN tags tag ON tag.id = item_tag.tag_id
        LEFT JOIN tags parent ON parent.id = tag.parent_id
        WHERE item_tag.item_id IN (:itemIds)
          AND tag.enabled = 1
        ORDER BY
            item_tag.item_id,
            COALESCE(parent.sort_order, tag.sort_order),
            CASE WHEN tag.parent_id IS NULL THEN 0 ELSE 1 END,
            tag.sort_order,
            tag.name COLLATE NOCASE,
            tag.id
        """
    )
    suspend fun getItemTags(itemIds: List<Long>): List<ReportItemTagRow>

    @Query(
        """
        SELECT
            quote.id AS quote_id,
            quote.item_id,
            item.title AS item_title,
            quote.content,
            quote.source,
            quote.chapter,
            quote.page,
            quote.created_time
        FROM quotes quote
        JOIN items item ON item.id = quote.item_id
        WHERE quote.created_time >= :startInclusive
          AND quote.created_time < :endExclusive
          AND item.deleted_at IS NULL
          AND (:typeCount = 0 OR item.type_id IN (:typeIds))
        ORDER BY quote.created_time, quote.id
        """
    )
    suspend fun getQuotes(
        startInclusive: Long,
        endExclusive: Long,
        typeIds: List<Long>,
        typeCount: Int
    ): List<ReportQuoteRow>
}
