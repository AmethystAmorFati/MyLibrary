package com.example.mylibrary.domain.usecase

import com.example.mylibrary.domain.model.LibraryStatus
import com.example.mylibrary.domain.model.StatusScope
import com.example.mylibrary.domain.repository.StatusRepository
import kotlinx.coroutines.flow.Flow

class ObserveManagedStatusesUseCase(private val repository: StatusRepository) {
    operator fun invoke(
        scope: StatusScope,
        includeDisabled: Boolean = false
    ): Flow<List<LibraryStatus>> =
        repository.observeStatuses(scope, includeDisabled)

    fun usageCounts(scope: StatusScope): Flow<Map<Long, Int>> =
        repository.observeUsageCounts(scope)
}

class CreateStatusUseCase(private val repository: StatusRepository) {
    suspend operator fun invoke(name: String, scope: StatusScope): Long {
        require(name.isNotBlank()) { "状态名称不能为空" }
        return repository.createStatus(name, scope)
    }
}

class RenameStatusUseCase(private val repository: StatusRepository) {
    suspend operator fun invoke(statusId: Long, name: String) {
        require(name.isNotBlank()) { "状态名称不能为空" }
        repository.renameStatus(statusId, name)
    }
}

class SetStatusEnabledUseCase(private val repository: StatusRepository) {
    suspend operator fun invoke(statusId: Long, enabled: Boolean) =
        repository.setStatusEnabled(statusId, enabled)
}

class DeleteStatusUseCase(private val repository: StatusRepository) {
    suspend operator fun invoke(statusId: Long) = repository.deleteStatus(statusId)
}

class ReorderStatusesUseCase(private val repository: StatusRepository) {
    suspend operator fun invoke(scope: StatusScope, orderedIds: List<Long>) =
        repository.reorderStatuses(scope, orderedIds)
}

data class StatusUseCases(
    val observe: ObserveManagedStatusesUseCase,
    val create: CreateStatusUseCase,
    val rename: RenameStatusUseCase,
    val setEnabled: SetStatusEnabledUseCase,
    val delete: DeleteStatusUseCase,
    val reorder: ReorderStatusesUseCase
)
