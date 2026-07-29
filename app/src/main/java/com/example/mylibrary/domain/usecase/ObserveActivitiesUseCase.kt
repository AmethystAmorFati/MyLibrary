package com.example.mylibrary.domain.usecase

import com.example.mylibrary.domain.model.LibraryActivity
import com.example.mylibrary.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.Flow

class ObserveActivitiesUseCase(
    private val repository: LibraryRepository
) {
    operator fun invoke(startDate: Long, endDate: Long): Flow<List<LibraryActivity>> {
        require(endDate >= startDate) { "日期范围无效" }
        return repository.observeActivities(startDate, endDate)
    }
}

