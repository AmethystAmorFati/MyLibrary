package com.example.mylibrary.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.mylibrary.data.entity.StatusEntity
import com.example.mylibrary.data.model.StatusUsageRow
import com.example.mylibrary.domain.model.StatusScope
import kotlinx.coroutines.flow.Flow

@Dao
interface StatusDao {
    @Query("SELECT * FROM statuses WHERE scope = :scope AND enabled = 1 ORDER BY sort_order, id")
    fun observeEnabled(scope: StatusScope): Flow<List<StatusEntity>>

    @Query("SELECT * FROM statuses WHERE scope = :scope ORDER BY sort_order, id")
    fun observeAll(scope: StatusScope): Flow<List<StatusEntity>>

    @Query("SELECT * FROM statuses WHERE scope = :scope ORDER BY sort_order, id")
    suspend fun getAll(scope: StatusScope): List<StatusEntity>

    @Query("SELECT * FROM statuses WHERE id = :statusId")
    suspend fun getById(statusId: Long): StatusEntity?

    @Query("SELECT COALESCE(MAX(sort_order), -1) FROM statuses WHERE scope = :scope")
    suspend fun getMaxSortOrder(scope: StatusScope): Int

    @Query(
        """
        SELECT status.id AS status_id, COUNT(item.id) AS usage_count
        FROM statuses status
        LEFT JOIN items item
          ON item.current_status_id = status.id
        WHERE status.scope = :scope
        GROUP BY status.id
        """
    )
    fun observeUsageCounts(scope: StatusScope): Flow<List<StatusUsageRow>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(status: StatusEntity): Long

    @Update
    suspend fun update(status: StatusEntity)

    @Query("UPDATE statuses SET sort_order = :sortOrder WHERE id = :statusId")
    suspend fun updateSortOrder(statusId: Long, sortOrder: Int)

    @Query("DELETE FROM statuses WHERE id = :statusId")
    suspend fun deleteById(statusId: Long)
}
