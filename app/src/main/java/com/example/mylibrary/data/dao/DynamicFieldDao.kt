package com.example.mylibrary.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.mylibrary.data.model.DynamicFieldValueRow
import com.example.mylibrary.data.model.FieldDefinitionRow
import com.example.mylibrary.data.model.ItemDynamicValueRow
import com.example.mylibrary.data.model.RecordDynamicValueRow
import com.example.mylibrary.data.model.StatisticFieldValueRow
import com.example.mylibrary.data.entity.FieldDefinitionEntity
import com.example.mylibrary.data.entity.FieldValueEntity
import com.example.mylibrary.data.entity.RecordFieldValueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DynamicFieldDao {
    @Query(
        "SELECT * FROM field_definitions " +
            "WHERE type_id = :typeId ORDER BY sort_order, id"
    )
    fun observeDefinitions(typeId: Long): Flow<List<FieldDefinitionEntity>>

    @Query(
        """
        SELECT
            definition.id,
            definition.type_id,
            item_type.name AS type_name,
            definition.name,
            definition.data_type,
            definition.enabled,
            definition.sort_order,
            definition.is_fixed,
            definition.options,
            definition.scope,
            definition.unit,
            definition.aggregations,
            EXISTS(
                SELECT 1 FROM field_values item_value
                WHERE item_value.field_id = definition.id
                UNION ALL
                SELECT 1 FROM record_field_values record_value
                WHERE record_value.field_id = definition.id
            ) AS has_values
        FROM field_definitions definition
        JOIN item_types item_type ON item_type.id = definition.type_id
        ORDER BY item_type.sort_order, definition.sort_order, definition.id
        """
    )
    fun observeAllDefinitions(): Flow<List<FieldDefinitionRow>>

    @Query(
        """
        SELECT
            definition.id AS definition_id,
            definition.name,
            definition.data_type,
            field_value.value,
            definition.sort_order,
            definition.is_fixed,
            definition.unit,
            definition.options
        FROM items item
        JOIN field_definitions definition ON definition.type_id = item.type_id
        LEFT JOIN field_values field_value
          ON field_value.item_id = item.id
         AND field_value.field_id = definition.id
        WHERE item.id = :itemId
          AND definition.enabled = 1
          AND definition.scope = 'item'
        ORDER BY definition.sort_order, definition.id
        """
    )
    fun observeFieldsForItem(itemId: Long): Flow<List<DynamicFieldValueRow>>

    @Query("SELECT * FROM field_values WHERE item_id = :itemId ORDER BY field_id")
    fun observeValues(itemId: Long): Flow<List<FieldValueEntity>>

    @Query(
        """
        SELECT
            field_value.item_id,
            field_value.field_id,
            field_value.value,
            definition.data_type,
            definition.options
        FROM field_values field_value
        JOIN field_definitions definition ON definition.id = field_value.field_id
        JOIN items item ON item.id = field_value.item_id
        WHERE definition.enabled = 1
          AND definition.is_fixed = 0
          AND definition.scope = 'item'
          AND item.deleted_at IS NULL
        ORDER BY field_value.item_id, definition.sort_order, definition.id
        """
    )
    fun observeActiveItemValues(): Flow<List<ItemDynamicValueRow>>

    @Query(
        """
        SELECT
            record_value.record_id,
            record_value.field_id,
            definition.name,
            definition.data_type,
            record_value.value,
            definition.sort_order,
            definition.unit,
            definition.options
        FROM record_field_values record_value
        JOIN records record ON record.id = record_value.record_id
        JOIN field_definitions definition ON definition.id = record_value.field_id
        WHERE record.item_id = :itemId
          AND definition.enabled = 1
          AND definition.scope = 'record'
        ORDER BY record_value.record_id, definition.sort_order, definition.id
        """
    )
    fun observeRecordValuesForItem(itemId: Long): Flow<List<RecordDynamicValueRow>>

    @Query(
        """
        SELECT definition.*
        FROM field_definitions definition
        JOIN item_types item_type ON item_type.id = definition.type_id
        WHERE definition.enabled = 1
          AND definition.is_fixed = 0
          AND definition.aggregations != ''
        ORDER BY item_type.sort_order, definition.sort_order, definition.id
        """
    )
    fun observeStatisticDefinitions(): Flow<List<FieldDefinitionEntity>>

    @Query(
        """
        SELECT
            field_value.field_id,
            field_value.item_id AS owner_id,
            field_value.value
        FROM field_values field_value
        JOIN field_definitions definition ON definition.id = field_value.field_id
        JOIN items item ON item.id = field_value.item_id
        WHERE definition.scope = 'item'
          AND definition.enabled = 1
          AND definition.is_fixed = 0
          AND definition.aggregations != ''
          AND item.deleted_at IS NULL
        ORDER BY definition.sort_order, definition.id, field_value.item_id
        """
    )
    fun observeItemStatisticValues(): Flow<List<StatisticFieldValueRow>>

    @Query(
        """
        SELECT
            record_value.field_id,
            record_value.record_id AS owner_id,
            record_value.value
        FROM record_field_values record_value
        JOIN field_definitions definition ON definition.id = record_value.field_id
        JOIN records record ON record.id = record_value.record_id
        JOIN items item ON item.id = record.item_id
        WHERE definition.scope = 'record'
          AND definition.enabled = 1
          AND definition.is_fixed = 0
          AND definition.aggregations != ''
          AND item.deleted_at IS NULL
        ORDER BY definition.sort_order, definition.id, record_value.record_id
        """
    )
    fun observeRecordStatisticValues(): Flow<List<StatisticFieldValueRow>>

    @Query(
        """
        SELECT * FROM field_definitions
        WHERE type_id = :typeId
          AND name IN ('author', 'director')
          AND enabled = 1
        ORDER BY sort_order, id
        LIMIT 1
        """
    )
    suspend fun getCreatorDefinition(typeId: Long): FieldDefinitionEntity?

    @Query("SELECT * FROM field_definitions WHERE id = :fieldId")
    suspend fun getDefinition(fieldId: Long): FieldDefinitionEntity?

    @Query("SELECT * FROM field_values WHERE field_id = :fieldId ORDER BY item_id")
    suspend fun getValuesForField(fieldId: Long): List<FieldValueEntity>

    @Query("SELECT * FROM record_field_values WHERE field_id = :fieldId ORDER BY record_id")
    suspend fun getRecordValuesForField(fieldId: Long): List<RecordFieldValueEntity>

    @Query(
        "SELECT * FROM field_values WHERE item_id = :itemId AND field_id = :fieldId LIMIT 1"
    )
    suspend fun getValue(itemId: Long, fieldId: Long): FieldValueEntity?

    @Query(
        "SELECT * FROM record_field_values " +
            "WHERE record_id = :recordId AND field_id = :fieldId LIMIT 1"
    )
    suspend fun getRecordValue(recordId: Long, fieldId: Long): RecordFieldValueEntity?

    @Query("SELECT * FROM record_field_values WHERE record_id = :recordId ORDER BY field_id")
    suspend fun getRecordValues(recordId: Long): List<RecordFieldValueEntity>

    @Query(
        """
        SELECT * FROM field_definitions
        WHERE type_id = :typeId
        ORDER BY sort_order, id
        """
    )
    suspend fun getDefinitions(typeId: Long): List<FieldDefinitionEntity>

    @Query(
        "SELECT COALESCE(MAX(sort_order), -1) FROM field_definitions WHERE type_id = :typeId"
    )
    suspend fun getMaxSortOrder(typeId: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDefinition(definition: FieldDefinitionEntity): Long

    @Update
    suspend fun updateDefinition(definition: FieldDefinitionEntity)

    @Query("DELETE FROM field_definitions WHERE id = :fieldId")
    suspend fun deleteDefinition(fieldId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceValue(value: FieldValueEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceRecordValue(value: RecordFieldValueEntity)

    @Query("DELETE FROM field_values WHERE item_id = :itemId AND field_id = :fieldId")
    suspend fun deleteValue(itemId: Long, fieldId: Long)

    @Query(
        "DELETE FROM record_field_values WHERE record_id = :recordId AND field_id = :fieldId"
    )
    suspend fun deleteRecordValue(recordId: Long, fieldId: Long)
}
