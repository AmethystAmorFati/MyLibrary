package com.example.mylibrary.domain.usecase

import com.example.mylibrary.domain.model.NewRecord
import com.example.mylibrary.domain.repository.LibraryRepository

class AddRecordUseCase(
    private val repository: LibraryRepository
) {
    suspend operator fun invoke(itemId: Long, record: NewRecord): Long {
        require(itemId > 0) { "作品编号无效" }
        require(record.endDate == null || record.endDate >= record.startDate) {
            "结束日期不能早于开始日期"
        }
        require(record.ratingHalfStars == null || record.ratingHalfStars in 1..10) {
            "评分应为 1 到 10 的半星值"
        }
        return repository.addRecord(itemId, record)
    }
}
