package com.example.mylibrary.data.repository

import androidx.room.withTransaction
import com.example.mylibrary.data.dao.ItemDao
import com.example.mylibrary.data.dao.TagDao
import com.example.mylibrary.data.database.LibraryDatabase
import com.example.mylibrary.data.entity.ItemTagEntity
import com.example.mylibrary.data.entity.TagEntity
import com.example.mylibrary.domain.model.LibraryTag
import com.example.mylibrary.domain.model.NewTag
import com.example.mylibrary.domain.repository.TagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class OfflineTagRepository(
    private val database: LibraryDatabase,
    private val itemDao: ItemDao,
    private val tagDao: TagDao
) : TagRepository {
    override fun observeTags(includeDisabled: Boolean): Flow<List<LibraryTag>> {
        val source = if (includeDisabled) tagDao.observeAll() else tagDao.observeEnabled()
        return source.map { tags -> tags.map { it.toDomain() } }
    }

    override fun observeItemTags(itemId: Long): Flow<List<LibraryTag>> =
        tagDao.observeForItem(itemId).map { tags -> tags.map { it.toDomain() } }

    override fun observeUsageCounts(): Flow<Map<Long, Int>> =
        tagDao.observeUsageCounts().map { rows ->
            rows.associate { it.tagId to it.usageCount }
        }

    override suspend fun createTag(tag: NewTag): Long =
        database.withTransaction {
            validateParent(tag.parentId)
            insertTag(tag.name, tag.parentId)
        }

    override suspend fun createTags(parentId: Long, names: List<String>): List<Long> =
        database.withTransaction {
            validateParent(parentId)
            val normalizedNames = names.map(String::trim)
            require(normalizedNames.isNotEmpty()) { "没有待添加的二级标签" }
            require(normalizedNames.none(String::isBlank)) { "标签名称不能为空" }
            check(normalizedNames.distinctBy(String::lowercase).size == normalizedNames.size) {
                "同一批次内存在重复标签"
            }
            val existingNames = tagDao.getEnabledSiblings(parentId)
                .map { it.name.lowercase() }
                .toHashSet()
            check(normalizedNames.none { it.lowercase() in existingNames }) {
                "同级标签名称已存在"
            }
            normalizedNames.map { name -> insertTag(name, parentId) }
        }

    override suspend fun renameTag(tagId: Long, name: String) {
        database.withTransaction {
            val tag = requireNotNull(tagDao.getById(tagId)) { "标签不存在" }
            ensureUniqueName(name, tag.parentId, tagId)
            tagDao.update(tag.copy(name = name.trim()))
        }
    }

    override suspend fun deleteTag(tagId: Long) {
        database.withTransaction {
            val tag = requireNotNull(tagDao.getById(tagId)) { "标签不存在" }
            if (tag.parentId == null) {
                tagDao.deleteChildren(tag.id)
            }
            tagDao.deleteById(tag.id)
        }
    }

    override suspend fun reorderTags(parentId: Long?, orderedIds: List<Long>) {
        database.withTransaction {
            validateParent(parentId)
            val siblings = tagDao.getEnabledSiblings(parentId)
            check(
                orderedIds.size == siblings.size &&
                    orderedIds.toSet() == siblings.mapTo(mutableSetOf()) { it.id }
            ) { "标签排序数据已变化，请重试" }
            orderedIds.forEachIndexed { index, id ->
                tagDao.updateSortOrder(id, index)
            }
        }
    }

    override suspend fun setItemTag(itemId: Long, tagId: Long, selected: Boolean) {
        requireNotNull(itemDao.getActiveEntity(itemId)) { "作品不存在" }
        val tag = requireNotNull(tagDao.getById(tagId)) { "标签不存在" }
        check(tag.enabled) { "标签已禁用" }
        if (selected) {
            tagDao.linkItem(ItemTagEntity(itemId, tagId))
        } else {
            tagDao.unlinkItem(itemId, tagId)
        }
    }

    private suspend fun ensureUniqueName(
        name: String,
        parentId: Long?,
        excludingId: Long? = null
    ) {
        val normalized = name.trim()
        require(normalized.isNotBlank()) { "标签名称不能为空" }
        check(
            tagDao.getEnabledSiblings(parentId).none {
                it.id != excludingId &&
                    it.name.equals(normalized, ignoreCase = true)
            }
        ) { "同级标签名称已存在" }
    }

    private suspend fun validateParent(parentId: Long?) {
        val parent = parentId?.let {
            requireNotNull(tagDao.getById(it)) { "一级标签不存在" }
        }
        check(parent?.parentId == null) { "只支持二级标签" }
        check(parent == null || parent.enabled) { "一级标签已不可用" }
    }

    private suspend fun insertTag(name: String, parentId: Long?): Long {
        ensureUniqueName(name, parentId)
        return tagDao.insert(
            TagEntity(
                name = name.trim(),
                parentId = parentId,
                sortOrder = tagDao.getMaxSortOrder(parentId) + 1
            )
        )
    }
}
