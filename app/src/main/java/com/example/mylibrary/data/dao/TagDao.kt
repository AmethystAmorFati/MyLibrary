package com.example.mylibrary.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.mylibrary.data.entity.ItemTagEntity
import com.example.mylibrary.data.entity.TagEntity
import com.example.mylibrary.data.model.ItemTagNameRow
import com.example.mylibrary.data.model.TagUsageRow
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Query(
        """
        SELECT tag.*
        FROM tags tag
        LEFT JOIN tags parent ON parent.id = tag.parent_id
        ORDER BY
            COALESCE(parent.sort_order, tag.sort_order),
            CASE WHEN tag.parent_id IS NULL THEN 0 ELSE 1 END,
            tag.sort_order,
            tag.name COLLATE NOCASE,
            tag.id
        """
    )
    fun observeAll(): Flow<List<TagEntity>>

    @Query(
        """
        SELECT tag.*
        FROM tags tag
        LEFT JOIN tags parent ON parent.id = tag.parent_id
        WHERE tag.enabled = 1
        ORDER BY
            COALESCE(parent.sort_order, tag.sort_order),
            CASE WHEN tag.parent_id IS NULL THEN 0 ELSE 1 END,
            tag.sort_order,
            tag.name COLLATE NOCASE,
            tag.id
        """
    )
    fun observeEnabled(): Flow<List<TagEntity>>

    @Query(
        """
        SELECT *
        FROM tags
        ORDER BY parent_id, sort_order, name COLLATE NOCASE, id
        """
    )
    suspend fun getAll(): List<TagEntity>

    @Query(
        """
        SELECT tag.*
        FROM tags tag
        JOIN item_tags item_tag ON item_tag.tag_id = tag.id
        LEFT JOIN tags parent ON parent.id = tag.parent_id
        WHERE item_tag.item_id = :itemId
          AND tag.enabled = 1
        ORDER BY
            COALESCE(parent.sort_order, tag.sort_order),
            CASE WHEN tag.parent_id IS NULL THEN 0 ELSE 1 END,
            tag.sort_order,
            tag.name COLLATE NOCASE,
            tag.id
        """
    )
    fun observeForItem(itemId: Long): Flow<List<TagEntity>>

    @Query(
        """
        SELECT
            item_tag.item_id,
            tag.name
        FROM item_tags item_tag
        JOIN tags tag ON tag.id = item_tag.tag_id
        JOIN items item ON item.id = item_tag.item_id
        LEFT JOIN tags parent ON parent.id = tag.parent_id
        WHERE tag.enabled = 1
          AND item.deleted_at IS NULL
        ORDER BY
            item_tag.item_id,
            COALESCE(parent.sort_order, tag.sort_order),
            CASE WHEN tag.parent_id IS NULL THEN 0 ELSE 1 END,
            tag.sort_order,
            tag.name COLLATE NOCASE,
            tag.id
        """
    )
    fun observeActiveItemTagNames(): Flow<List<ItemTagNameRow>>

    @Query(
        """
        SELECT tag.id AS tag_id, COUNT(item_tag.item_id) AS usage_count
        FROM tags tag
        LEFT JOIN item_tags item_tag ON item_tag.tag_id = tag.id
        GROUP BY tag.id
        """
    )
    fun observeUsageCounts(): Flow<List<TagUsageRow>>

    @Query("SELECT * FROM tags WHERE id = :tagId")
    suspend fun getById(tagId: Long): TagEntity?

    @Query(
        """
        SELECT *
        FROM tags
        WHERE enabled = 1
          AND (
              (:parentId IS NULL AND parent_id IS NULL)
              OR parent_id = :parentId
          )
        ORDER BY sort_order, name COLLATE NOCASE, id
        """
    )
    suspend fun getEnabledSiblings(parentId: Long?): List<TagEntity>

    @Query(
        """
        SELECT *
        FROM tags
        WHERE parent_id = :parentId
        ORDER BY sort_order, name COLLATE NOCASE, id
        """
    )
    suspend fun getChildren(parentId: Long): List<TagEntity>

    @Query(
        """
        SELECT COALESCE(MAX(sort_order), -1)
        FROM tags
        WHERE (:parentId IS NULL AND parent_id IS NULL)
           OR parent_id = :parentId
        """
    )
    suspend fun getMaxSortOrder(parentId: Long?): Int

    @Query("SELECT COUNT(*) FROM item_tags WHERE tag_id = :tagId")
    suspend fun getUsageCount(tagId: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(tag: TagEntity): Long

    @Update
    suspend fun update(tag: TagEntity)

    @Query("UPDATE tags SET sort_order = :sortOrder WHERE id = :tagId")
    suspend fun updateSortOrder(tagId: Long, sortOrder: Int)

    @Query("DELETE FROM tags WHERE parent_id = :parentId")
    suspend fun deleteChildren(parentId: Long)

    @Query("DELETE FROM tags WHERE id = :tagId")
    suspend fun deleteById(tagId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkItem(itemTag: ItemTagEntity)

    @Query("DELETE FROM item_tags WHERE item_id = :itemId AND tag_id = :tagId")
    suspend fun unlinkItem(itemId: Long, tagId: Long)

    @Query("DELETE FROM item_tags WHERE item_id = :itemId")
    suspend fun unlinkAllForItem(itemId: Long)
}
