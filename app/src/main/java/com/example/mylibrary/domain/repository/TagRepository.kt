package com.example.mylibrary.domain.repository

import com.example.mylibrary.domain.model.LibraryTag
import com.example.mylibrary.domain.model.NewTag
import kotlinx.coroutines.flow.Flow

interface TagRepository {
    fun observeTags(includeDisabled: Boolean = false): Flow<List<LibraryTag>>
    fun observeItemTags(itemId: Long): Flow<List<LibraryTag>>
    fun observeUsageCounts(): Flow<Map<Long, Int>>

    suspend fun createTag(tag: NewTag): Long
    suspend fun createTags(parentId: Long, names: List<String>): List<Long>
    suspend fun renameTag(tagId: Long, name: String)
    suspend fun deleteTag(tagId: Long)
    suspend fun reorderTags(parentId: Long?, orderedIds: List<Long>)
    suspend fun setItemTag(itemId: Long, tagId: Long, selected: Boolean)
}
