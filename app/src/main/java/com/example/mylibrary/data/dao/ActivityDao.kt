package com.example.mylibrary.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.mylibrary.data.entity.ActivityEntity
import com.example.mylibrary.data.model.ActivityRecordDateRow
import com.example.mylibrary.data.model.ActivityRow
import com.example.mylibrary.data.model.VisualExportActivityRow
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDao {
    @Query(
        """
        SELECT
            activity.id,
            activity.date,
            activity.item_id,
            item.type_id,
            activity.record_id,
            COALESCE(record.created_at, activity.date) AS record_created_at,
            item.title,
            item_type.name AS type_name,
            item.thumbnail_path
        FROM activities activity
        INNER JOIN items item ON item.id = activity.item_id
        INNER JOIN item_types item_type ON item_type.id = item.type_id
        LEFT JOIN records record ON record.id = activity.record_id
        WHERE activity.date BETWEEN :startDate AND :endDate
          AND item.deleted_at IS NULL
        ORDER BY
            activity.date DESC,
            record_created_at DESC,
            COALESCE(activity.record_id, 0) DESC,
            activity.id DESC
        """
    )
    fun observeRowsBetween(startDate: Long, endDate: Long): Flow<List<ActivityRow>>

    @Query(
        """
        SELECT
            activity.id AS activity_id,
            activity.date,
            activity.item_id,
            item.type_id,
            activity.record_id,
            COALESCE(record.created_at, activity.date) AS record_created_at,
            item.title,
            item.cover_path,
            item.thumbnail_path
        FROM activities activity
        INNER JOIN items item ON item.id = activity.item_id
        LEFT JOIN records record ON record.id = activity.record_id
        WHERE activity.date BETWEEN :startDate AND :endDate
          AND item.deleted_at IS NULL
        ORDER BY
            activity.date ASC,
            record_created_at ASC,
            COALESCE(activity.record_id, 0) ASC,
            activity.id ASC
        """
    )
    suspend fun getVisualExportRowsBetween(
        startDate: Long,
        endDate: Long
    ): List<VisualExportActivityRow>

    @Query(
        """
        SELECT
            record.id AS record_id,
            activity.date
        FROM activities activity
        INNER JOIN records record ON record.id = activity.record_id
        INNER JOIN items item ON item.id = activity.item_id
        WHERE item.deleted_at IS NULL
        ORDER BY record.id ASC, activity.date ASC
        """
    )
    fun observeRecordDates(): Flow<List<ActivityRecordDateRow>>

    @Query("DELETE FROM activities WHERE item_id = :itemId")
    suspend fun deleteForItem(itemId: Long)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(activities: List<ActivityEntity>)
}
