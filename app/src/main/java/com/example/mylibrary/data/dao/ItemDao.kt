package com.example.mylibrary.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.mylibrary.data.entity.ItemEntity
import com.example.mylibrary.data.model.LibraryItemRow
import com.example.mylibrary.data.model.TrashItemRow
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query(
        LIBRARY_ITEM_SELECT +
            """
            WHERE i.deleted_at IS NULL
              AND (
                :query = ''
                OR i.title LIKE '%' || :query || '%'
                OR EXISTS (
                    SELECT 1
                    FROM field_values search_value
                    JOIN field_definitions search_field
                      ON search_field.id = search_value.field_id
                    WHERE search_value.item_id = i.id
                      AND search_field.name IN ('author', 'director')
                      AND search_value.value LIKE '%' || :query || '%'
                )
              )
              AND (
                :statusId IS NULL
                OR i.current_status_id = :statusId
              )
              AND (
                :tagCount = 0
                OR (
                    SELECT COUNT(DISTINCT selected_item_tag.tag_id)
                    FROM item_tags selected_item_tag
                    JOIN tags selected_tag ON selected_tag.id = selected_item_tag.tag_id
                    WHERE selected_item_tag.item_id = i.id
                      AND selected_tag.enabled = 1
                      AND selected_item_tag.tag_id IN (:tagIds)
                ) = :tagCount
              )
            ORDER BY i.updated_time DESC, i.id DESC
            """
    )
    fun observeLibraryRows(
        query: String,
        statusId: Long?,
        tagIds: List<Long>,
        tagCount: Int
    ): Flow<List<LibraryItemRow>>

    @Query(
        LIBRARY_ITEM_SELECT +
            "WHERE i.id = :itemId AND i.deleted_at IS NULL LIMIT 1"
    )
    fun observeById(itemId: Long): Flow<LibraryItemRow?>

    @Query("SELECT * FROM items WHERE id = :itemId AND deleted_at IS NULL")
    suspend fun getActiveEntity(itemId: Long): ItemEntity?

    @Query("SELECT * FROM items WHERE id = :itemId")
    suspend fun getEntity(itemId: Long): ItemEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: ItemEntity): Long

    @Update
    suspend fun update(item: ItemEntity)

    @Query(
        """
        UPDATE items
        SET current_status_id = :statusId, updated_time = :updatedTime
        WHERE id = :itemId AND deleted_at IS NULL
        """
    )
    suspend fun updateCurrentStatus(
        itemId: Long,
        statusId: Long,
        updatedTime: Long
    ): Int

    @Query(
        """
        UPDATE items
        SET deleted_at = :deletedAt, updated_time = :deletedAt
        WHERE id = :itemId AND deleted_at IS NULL
        """
    )
    suspend fun softDelete(itemId: Long, deletedAt: Long): Int

    @Query(
        """
        SELECT
            item.id,
            item.type_id,
            item_type.name AS type_name,
            item.title,
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
            item.cover_path,
            item.thumbnail_path,
            item.deleted_at
        FROM items item
        INNER JOIN item_types item_type ON item_type.id = item.type_id
        WHERE item.deleted_at IS NOT NULL
        ORDER BY item.deleted_at DESC, item.id DESC
        """
    )
    fun observeTrashRows(): Flow<List<TrashItemRow>>

    @Query("SELECT * FROM items WHERE id = :itemId AND deleted_at IS NOT NULL")
    suspend fun getDeletedEntity(itemId: Long): ItemEntity?

    @Query(
        """
        SELECT *
        FROM items
        WHERE deleted_at IS NOT NULL
          AND id IN (:itemIds)
        ORDER BY id
        """
    )
    suspend fun getDeletedEntities(itemIds: List<Long>): List<ItemEntity>

    @Query("SELECT * FROM items WHERE deleted_at IS NOT NULL ORDER BY id")
    suspend fun getAllDeletedEntities(): List<ItemEntity>

    @Query(
        """
        UPDATE items
        SET deleted_at = NULL, updated_time = :restoredAt
        WHERE id = :itemId AND deleted_at IS NOT NULL
        """
    )
    suspend fun restore(itemId: Long, restoredAt: Long): Int

    @Query("DELETE FROM items WHERE id = :itemId AND deleted_at IS NOT NULL")
    suspend fun permanentlyDelete(itemId: Long): Int

    @Query("DELETE FROM items WHERE deleted_at IS NOT NULL AND id IN (:itemIds)")
    suspend fun permanentlyDeleteItems(itemIds: List<Long>): Int

    @Query("DELETE FROM items WHERE deleted_at IS NOT NULL")
    suspend fun permanentlyDeleteAllTrash(): Int

    @Query(
        """
        SELECT COUNT(*)
        FROM items
        WHERE cover_path = :path OR thumbnail_path = :path
        """
    )
    suspend fun countCoverPathReferences(path: String): Int
}

private const val LIBRARY_ITEM_SELECT =
    """
    SELECT
        i.id,
        i.type_id,
        item_type.name AS type_name,
        i.title,
        (
            SELECT creator_value.value
            FROM field_values creator_value
            JOIN field_definitions creator_field
              ON creator_field.id = creator_value.field_id
            WHERE creator_value.item_id = i.id
              AND creator_field.name IN ('author', 'director')
              AND creator_field.enabled = 1
            ORDER BY creator_field.sort_order, creator_field.id
            LIMIT 1
        ) AS creator,
        i.cover_path,
        i.thumbnail_path,
        i.current_status_id,
        i.created_time,
        i.updated_time,
        current_status.name AS current_status_name
        ,
        (
            SELECT latest_record.rating_half_stars
            FROM records latest_record
            WHERE latest_record.item_id = i.id
              AND latest_record.rating_half_stars IS NOT NULL
            ORDER BY latest_record.start_date DESC, latest_record.id DESC
            LIMIT 1
        ) AS latest_rating_half_stars,
        (
            SELECT SUM(duration_record.duration_minutes)
            FROM records duration_record
            WHERE duration_record.item_id = i.id
              AND duration_record.duration_minutes IS NOT NULL
        ) AS total_duration_minutes
    FROM items i
    JOIN item_types item_type ON item_type.id = i.type_id
    LEFT JOIN statuses current_status ON current_status.id = i.current_status_id
    """
