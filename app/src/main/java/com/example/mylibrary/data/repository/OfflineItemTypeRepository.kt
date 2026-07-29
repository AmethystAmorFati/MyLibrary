package com.example.mylibrary.data.repository

import androidx.room.withTransaction
import com.example.mylibrary.data.dao.DynamicFieldDao
import com.example.mylibrary.data.dao.ItemTypeDao
import com.example.mylibrary.data.database.DefaultLibraryData
import com.example.mylibrary.data.database.LibraryDatabase
import com.example.mylibrary.data.entity.FieldDefinitionEntity
import com.example.mylibrary.data.entity.ItemTypeEntity
import com.example.mylibrary.domain.model.FieldDataType
import com.example.mylibrary.domain.model.ItemType
import com.example.mylibrary.domain.repository.ItemTypeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class OfflineItemTypeRepository(
    private val database: LibraryDatabase,
    private val typeDao: ItemTypeDao,
    private val fieldDao: DynamicFieldDao
) : ItemTypeRepository {
    override fun observeTypes(): Flow<List<ItemType>> =
        typeDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeUsageCounts(): Flow<Map<Long, Int>> =
        typeDao.observeUsageCounts().map { rows ->
            rows.associate { it.typeId to it.usageCount }
        }

    override suspend fun createType(name: String): Long =
        database.withTransaction {
            val normalized = normalizedUniqueName(name)
            val typeId = typeDao.insert(
                ItemTypeEntity(
                    name = normalized,
                    sortOrder = typeDao.getMaxSortOrder() + 1
                )
            )
            fieldDao.insertDefinition(
                FieldDefinitionEntity(
                    typeId = typeId,
                    name = "author",
                    dataType = FieldDataType.TEXT,
                    sortOrder = 0,
                    isFixed = true
                )
            )
            typeId
        }

    override suspend fun renameType(typeId: Long, name: String) {
        database.withTransaction {
            val type = requireNotNull(typeDao.getById(typeId)) { "作品类型不存在" }
            typeDao.update(type.copy(name = normalizedUniqueName(name, typeId)))
        }
    }

    override suspend fun deleteType(typeId: Long) {
        database.withTransaction {
            check(typeId !in builtInTypeIds) { "内置作品类型不可删除" }
            val type = requireNotNull(typeDao.getById(typeId)) { "作品类型不存在" }
            val usageCount = typeDao.observeUsageCounts()
                .map { rows -> rows.firstOrNull { it.typeId == typeId }?.usageCount ?: 0 }
                .first()
            check(usageCount == 0) {
                "仍有 $usageCount 部作品属于“${type.name}”，请先迁移作品类型"
            }
            typeDao.deleteById(typeId)
        }
    }

    override suspend fun reorderTypes(orderedIds: List<Long>) {
        database.withTransaction {
            val types = typeDao.getAll()
            check(
                orderedIds.size == types.size &&
                    orderedIds.toSet() == types.mapTo(mutableSetOf()) { it.id }
            ) { "作品类型排序数据已变化，请重试" }
            orderedIds.forEachIndexed { index, id ->
                typeDao.updateSortOrder(id, index)
            }
        }
    }

    private suspend fun normalizedUniqueName(
        name: String,
        excludingId: Long? = null
    ): String {
        val normalized = name.trim()
        require(normalized.isNotBlank()) { "作品类型名称不能为空" }
        check(
            typeDao.getAll().none {
                it.id != excludingId && it.name.equals(normalized, ignoreCase = true)
            }
        ) { "作品类型名称已存在" }
        return normalized
    }

    private companion object {
        val builtInTypeIds = setOf(
            DefaultLibraryData.BOOK_TYPE_ID,
            DefaultLibraryData.MOVIE_TYPE_ID
        )
    }
}
