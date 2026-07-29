package com.example.mylibrary.domain.usecase

import com.example.mylibrary.domain.model.LibraryStatus
import com.example.mylibrary.domain.repository.LibraryRepository
import com.example.mylibrary.domain.model.StatusScope
import kotlinx.coroutines.flow.Flow

class ObserveStatusesUseCase(
    private val repository: LibraryRepository
) {
    operator fun invoke(
        scope: StatusScope = StatusScope.ITEM
    ): Flow<List<LibraryStatus>> = repository.observeStatuses(scope)
}
