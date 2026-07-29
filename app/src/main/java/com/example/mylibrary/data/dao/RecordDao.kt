package com.example.mylibrary.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import com.example.mylibrary.data.entity.RecordEntity
import com.example.mylibrary.data.model.RecordRow
import com.example.mylibrary.data.model.TimelineRecordRow
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {
    @Query(
        """
        SELECT
            record.id,
            record.item_id,
            record.start_date,
            record.end_date,
            record.rating_half_stars,
            record.review,
            record.status_snapshot,
            record.duration_minutes,
            record.created_at
        FROM records record
        WHERE record.item_id = :itemId
        ORDER BY record.start_date DESC, record.created_at DESC, record.id DESC
        """
    )
    fun observeForItem(itemId: Long): Flow<List<RecordRow>>

    @Query(
        """
        SELECT
            record.id AS record_id,
            record.start_date AS record_start_date,
            record.created_at,
            record.item_id,
            item.type_id,
            item.title,
            item_type.name AS type_name,
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
            ) AS creator,
            record.rating_half_stars,
            item.thumbnail_path,
            record.status_snapshot,
            record.duration_minutes
        FROM records record
        INNER JOIN items item ON item.id = record.item_id
        INNER JOIN item_types item_type ON item_type.id = item.type_id
        WHERE record.start_date BETWEEN :startDate AND :endDate
          AND item.deleted_at IS NULL
        ORDER BY
            record.start_date ASC,
            record.created_at ASC,
            record.id ASC
        """
    )
    fun observeTimelineBetween(
        startDate: Long,
        endDate: Long
    ): Flow<List<TimelineRecordRow>>

    @Query("SELECT * FROM records WHERE id = :recordId")
    suspend fun getById(recordId: Long): RecordEntity?

    @Query(
        """
        SELECT * FROM records
        WHERE item_id = :itemId
        ORDER BY start_date DESC, id DESC
        LIMIT 1
        """
    )
    suspend fun getLatestForItem(itemId: Long): RecordEntity?

    @Query(
        """
        SELECT * FROM records
        WHERE item_id = :itemId
        ORDER BY start_date DESC, id DESC
        """
    )
    suspend fun getAllForItem(itemId: Long): List<RecordEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: RecordEntity): Long

    @Update
    suspend fun update(record: RecordEntity)

    @Delete
    suspend fun delete(record: RecordEntity)
}
