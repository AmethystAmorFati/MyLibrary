package com.example.mylibrary.domain.usecase

import com.example.mylibrary.domain.model.CustomFieldStatistic
import com.example.mylibrary.domain.repository.CustomFieldStatisticsRepository
import kotlinx.coroutines.flow.Flow

class ObserveCustomFieldStatisticsUseCase(
    private val repository: CustomFieldStatisticsRepository
) {
    operator fun invoke(): Flow<List<CustomFieldStatistic>> =
        repository.observeStatistics()
}
