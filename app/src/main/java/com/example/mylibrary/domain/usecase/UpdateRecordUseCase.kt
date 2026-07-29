package com.example.mylibrary.domain.usecase

import com.example.mylibrary.domain.model.RecordChanges
import com.example.mylibrary.domain.repository.LibraryRepository

class UpdateRecordUseCase(
    private val repository: LibraryRepository
) {
    suspend operator fun invoke(recordId: Long, changes: RecordChanges) {
        require(recordId > 0) { "记录编号无效" }
        require(changes.endDate == null || changes.endDate >= changes.startDate) {
            "结束日期不能早于开始日期"
        }
        require(changes.ratingHalfStars == null || changes.ratingHalfStars in 1..10) {
            "评分应为 1 到 10 的半星值"
        }
        repository.updateRecord(recordId, changes)
    }
}
