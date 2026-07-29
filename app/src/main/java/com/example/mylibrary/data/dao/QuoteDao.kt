package com.example.mylibrary.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.mylibrary.data.entity.QuoteEntity
import com.example.mylibrary.data.model.QuoteListRow
import com.example.mylibrary.data.model.MediaItemStatisticsRow
import com.example.mylibrary.data.model.QuoteStatisticsRow
import com.example.mylibrary.data.model.QuoteTagStatisticRow
import kotlinx.coroutines.flow.Flow

@Dao
interface QuoteDao {
    @Query(
        QUOTE_LIST_SELECT +
            """
            WHERE item.deleted_at IS NULL
            ORDER BY quote.created_time DESC, quote.id DESC
            LIMIT 5
            """
    )
    fun observeRecent(): Flow<List<QuoteListRow>>

    @Query(
        QUOTE_LIST_SELECT +
            """
            WHERE item.deleted_at IS NULL
              AND (
                :query = ''
                OR quote.content LIKE '%' || :query || '%'
                OR quote.chapter LIKE '%' || :query || '%'
                OR item.title LIKE '%' || :query || '%'
                OR EXISTS (
                    SELECT 1
                    FROM field_values search_value
                    JOIN field_definitions search_field
                      ON search_field.id = search_value.field_id
                    WHERE search_value.item_id = item.id
                      AND search_field.name IN ('author', 'director')
                      AND search_value.value LIKE '%' || :query || '%'
                )
              )
            ORDER BY quote.created_time DESC, quote.id DESC
            LIMIT :limit OFFSET :offset
            """
    )
    fun observePage(
        query: String,
        limit: Int,
        offset: Int
    ): Flow<List<QuoteListRow>>

    @Query(
        """
        SELECT *
        FROM quotes
        WHERE item_id = :itemId
        ORDER BY created_time DESC, id DESC
        """
    )
    fun observeForItem(itemId: Long): Flow<List<QuoteEntity>>

    @Query(
        """
        SELECT *
        FROM quotes
        WHERE item_id = :itemId
        ORDER BY created_time DESC, id DESC
        """
    )
    suspend fun getAllForItem(itemId: Long): List<QuoteEntity>

    @Query(
        """
        SELECT
            (
                SELECT COUNT(DISTINCT item.id)
                FROM items item
                INNER JOIN records record ON record.item_id = item.id
                WHERE item.deleted_at IS NULL AND item.type_id = 1
            ) AS reading_work_count,
            (
                SELECT COUNT(DISTINCT item.id)
                FROM items item
                INNER JOIN records record ON record.item_id = item.id
                WHERE item.deleted_at IS NULL AND item.type_id = 2
            ) AS viewing_work_count,
            (
                SELECT COUNT(*)
                FROM quotes quote_count
                JOIN items quote_item ON quote_item.id = quote_count.item_id
                WHERE quote_item.deleted_at IS NULL
            ) AS quote_count,
            (
                SELECT COUNT(*)
                FROM tags
                WHERE enabled = 1
            ) AS tag_count
        """
    )
    fun observeStatistics(): Flow<QuoteStatisticsRow>

    @Query(
        """
        WITH record_aggregates AS (
            SELECT
                item_id,
                COUNT(*) AS record_count,
                COUNT(duration_minutes) AS valued_record_count,
                SUM(duration_minutes) AS total_duration_minutes,
                MAX(duration_minutes) AS maximum_single_duration_minutes
            FROM records
            GROUP BY item_id
        ),
        quote_aggregates AS (
            SELECT
                item_id,
                COUNT(*) AS quote_count
            FROM quotes
            GROUP BY item_id
        )
        SELECT
            item.id AS item_id,
            item.type_id AS type_id,
            item.title AS item_title,
            COALESCE(record_aggregates.record_count, 0) AS record_count,
            COALESCE(quote_aggregates.quote_count, 0) AS quote_count,
            COALESCE(record_aggregates.valued_record_count, 0)
                AS valued_record_count,
            record_aggregates.total_duration_minutes AS total_duration_minutes,
            record_aggregates.maximum_single_duration_minutes
                AS maximum_single_duration_minutes
        FROM items item
        LEFT JOIN record_aggregates
          ON record_aggregates.item_id = item.id
        LEFT JOIN quote_aggregates
          ON quote_aggregates.item_id = item.id
        WHERE item.deleted_at IS NULL
          AND item.type_id IN (:bookTypeId, :movieTypeId)
        ORDER BY item.type_id, item.title COLLATE NOCASE, item.id
        """
    )
    fun observeMediaItemStatistics(
        bookTypeId: Long,
        movieTypeId: Long
    ): Flow<List<MediaItemStatisticsRow>>

    @Query(
        """
        SELECT
            CASE
                WHEN parent.id IS NULL THEN tag.name
                ELSE parent.name || ' / ' || tag.name
            END AS name,
            COUNT(DISTINCT item_tag.item_id) AS usage_count
        FROM tags tag
        JOIN item_tags item_tag ON item_tag.tag_id = tag.id
        JOIN items item ON item.id = item_tag.item_id
        LEFT JOIN tags parent ON parent.id = tag.parent_id
        WHERE tag.enabled = 1
          AND item.deleted_at IS NULL
        GROUP BY tag.id
        ORDER BY usage_count DESC, tag.sort_order, tag.id
        LIMIT 8
        """
    )
    fun observeTagStatistics(): Flow<List<QuoteTagStatisticRow>>

    @Query("SELECT * FROM quotes WHERE id = :quoteId LIMIT 1")
    suspend fun getById(quoteId: Long): QuoteEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(quote: QuoteEntity): Long

    @Update
    suspend fun update(quote: QuoteEntity)

    @Delete
    suspend fun delete(quote: QuoteEntity)
}

private const val QUOTE_LIST_SELECT =
    """
    SELECT
        quote.id,
        quote.item_id,
        quote.content,
        quote.chapter,
        quote.page,
        quote.created_time,
        item.title AS item_title,
        COALESCE(
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
            ),
            ''
        ) AS creator
    FROM quotes quote
    JOIN items item ON item.id = quote.item_id
    """
