package com.example.mylibrary.domain.usecase

import com.example.mylibrary.domain.model.LibraryTimelineRecord
import com.example.mylibrary.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.Flow

class ObserveTimelineRecordsUseCase(
    private val repository: LibraryRepository
) {
    operator fun invoke(
        startDate: Long,
        endDate: Long
    ): Flow<List<LibraryTimelineRecord>> {
        require(endDate >= startDate) { "日期范围无效" }
        return repository.observeTimelineRecords(startDate, endDate)
    }
}
