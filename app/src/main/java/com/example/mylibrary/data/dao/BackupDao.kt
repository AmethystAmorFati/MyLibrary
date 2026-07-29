package com.example.mylibrary.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.mylibrary.data.entity.ActivityEntity
import com.example.mylibrary.data.entity.FieldDefinitionEntity
import com.example.mylibrary.data.entity.FieldValueEntity
import com.example.mylibrary.data.entity.ItemEntity
import com.example.mylibrary.data.entity.ItemTagEntity
import com.example.mylibrary.data.entity.ItemTypeEntity
import com.example.mylibrary.data.entity.QuoteEntity
import com.example.mylibrary.data.entity.RecordEntity
import com.example.mylibrary.data.entity.RecordFieldValueEntity
import com.example.mylibrary.data.entity.StatusEntity
import com.example.mylibrary.data.entity.TagEntity

@Dao
interface BackupDao {
    @Query("SELECT * FROM item_types ORDER BY sort_order, id")
    suspend fun getItemTypes(): List<ItemTypeEntity>

    @Query("SELECT * FROM statuses ORDER BY scope, sort_order, id")
    suspend fun getStatuses(): List<StatusEntity>

    @Query("SELECT * FROM field_definitions ORDER BY type_id, sort_order, id")
    suspend fun getFieldDefinitions(): List<FieldDefinitionEntity>

    @Query("SELECT * FROM tags ORDER BY parent_id, sort_order, id")
    suspend fun getTags(): List<TagEntity>

    @Query("SELECT * FROM items ORDER BY id")
    suspend fun getItems(): List<ItemEntity>

    @Query("SELECT * FROM records ORDER BY id")
    suspend fun getRecords(): List<RecordEntity>

    @Query("SELECT * FROM activities ORDER BY id")
    suspend fun getActivities(): List<ActivityEntity>

    @Query("SELECT * FROM item_tags ORDER BY item_id, tag_id")
    suspend fun getItemTags(): List<ItemTagEntity>

    @Query("SELECT * FROM field_values ORDER BY id")
    suspend fun getFieldValues(): List<FieldValueEntity>

    @Query("SELECT * FROM record_field_values ORDER BY id")
    suspend fun getRecordFieldValues(): List<RecordFieldValueEntity>

    @Query("SELECT * FROM quotes ORDER BY id")
    suspend fun getQuotes(): List<QuoteEntity>

    @Query("DELETE FROM activities")
    suspend fun deleteActivities()

    @Query("DELETE FROM quotes")
    suspend fun deleteQuotes()

    @Query("DELETE FROM item_tags")
    suspend fun deleteItemTags()

    @Query("DELETE FROM field_values")
    suspend fun deleteFieldValues()

    @Query("DELETE FROM record_field_values")
    suspend fun deleteRecordFieldValues()

    @Query("DELETE FROM records")
    suspend fun deleteRecords()

    @Query("DELETE FROM items")
    suspend fun deleteItems()

    @Query("DELETE FROM field_definitions")
    suspend fun deleteFieldDefinitions()

    @Query("DELETE FROM tags")
    suspend fun deleteTags()

    @Query("DELETE FROM statuses")
    suspend fun deleteStatuses()

    @Query("DELETE FROM item_types")
    suspend fun deleteItemTypes()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertItemTypes(values: List<ItemTypeEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStatuses(values: List<StatusEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFieldDefinitions(values: List<FieldDefinitionEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTags(values: List<TagEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertItems(values: List<ItemEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRecords(values: List<RecordEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertActivities(values: List<ActivityEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertItemTags(values: List<ItemTagEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFieldValues(values: List<FieldValueEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRecordFieldValues(values: List<RecordFieldValueEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertQuotes(values: List<QuoteEntity>)
}
