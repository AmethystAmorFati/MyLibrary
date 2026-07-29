package com.example.mylibrary.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.mylibrary.data.entity.ItemTypeEntity
import com.example.mylibrary.data.model.ItemTypeUsageRow
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemTypeDao {
    @Query("SELECT * FROM item_types ORDER BY sort_order, id")
    fun observeAll(): Flow<List<ItemTypeEntity>>

    @Query("SELECT * FROM item_types ORDER BY sort_order, id")
    suspend fun getAll(): List<ItemTypeEntity>

    @Query("SELECT * FROM item_types WHERE id = :typeId")
    suspend fun getById(typeId: Long): ItemTypeEntity?

    @Query("SELECT COALESCE(MAX(sort_order), -1) FROM item_types")
    suspend fun getMaxSortOrder(): Int

    @Query(
        """
        SELECT type.id AS type_id, COUNT(item.id) AS usage_count
        FROM item_types type
        LEFT JOIN items item
          ON item.type_id = type.id
        GROUP BY type.id
        """
    )
    fun observeUsageCounts(): Flow<List<ItemTypeUsageRow>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(type: ItemTypeEntity): Long

    @Update
    suspend fun update(type: ItemTypeEntity)

    @Query("UPDATE item_types SET sort_order = :sortOrder WHERE id = :typeId")
    suspend fun updateSortOrder(typeId: Long, sortOrder: Int)

    @Query("DELETE FROM item_types WHERE id = :typeId")
    suspend fun deleteById(typeId: Long)
}
