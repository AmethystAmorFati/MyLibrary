package com.example.mylibrary.domain.repository

import com.example.mylibrary.domain.model.CustomFieldStatistic
import kotlinx.coroutines.flow.Flow

interface CustomFieldStatisticsRepository {
    fun observeStatistics(): Flow<List<CustomFieldStatistic>>
}
