package com.example.mylibrary.domain.usecase

import com.example.mylibrary.domain.model.LibraryTag
import com.example.mylibrary.domain.model.NewTag
import com.example.mylibrary.domain.repository.TagRepository
import kotlinx.coroutines.flow.Flow

class ObserveTagsUseCase(private val repository: TagRepository) {
    operator fun invoke(includeDisabled: Boolean = false): Flow<List<LibraryTag>> =
        repository.observeTags(includeDisabled)

    fun forItem(itemId: Long): Flow<List<LibraryTag>> =
        repository.observeItemTags(itemId)

    fun usageCounts(): Flow<Map<Long, Int>> =
        repository.observeUsageCounts()
}

class CreateTagUseCase(private val repository: TagRepository) {
    suspend operator fun invoke(tag: NewTag): Long {
        require(tag.name.isNotBlank()) { "标签名称不能为空" }
        return repository.createTag(tag)
    }
}

class RenameTagUseCase(private val repository: TagRepository) {
    suspend operator fun invoke(tagId: Long, name: String) {
        require(name.isNotBlank()) { "标签名称不能为空" }
        repository.renameTag(tagId, name)
    }
}

class CreateChildTagsUseCase(private val repository: TagRepository) {
    suspend operator fun invoke(parentId: Long, names: List<String>): List<Long> {
        require(names.isNotEmpty()) { "没有待添加的二级标签" }
        return repository.createTags(parentId, names)
    }
}

class DeleteTagUseCase(private val repository: TagRepository) {
    suspend operator fun invoke(tagId: Long) = repository.deleteTag(tagId)
}

class ReorderTagsUseCase(private val repository: TagRepository) {
    suspend operator fun invoke(parentId: Long?, orderedIds: List<Long>) =
        repository.reorderTags(parentId, orderedIds)
}

class SetItemTagUseCase(private val repository: TagRepository) {
    suspend operator fun invoke(itemId: Long, tagId: Long, selected: Boolean) =
        repository.setItemTag(itemId, tagId, selected)
}

data class TagUseCases(
    val observe: ObserveTagsUseCase,
    val create: CreateTagUseCase,
    val createChildren: CreateChildTagsUseCase,
    val rename: RenameTagUseCase,
    val delete: DeleteTagUseCase,
    val reorder: ReorderTagsUseCase,
    val setItemTag: SetItemTagUseCase
)
