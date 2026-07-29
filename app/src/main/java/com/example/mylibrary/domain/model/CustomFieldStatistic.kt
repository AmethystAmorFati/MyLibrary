package com.example.mylibrary.domain.model

sealed interface CustomFieldStatistic {
    val fieldId: Long
    val fieldName: String
    val sortOrder: Int

    data class Numeric(
        override val fieldId: Long,
        override val fieldName: String,
        override val sortOrder: Int,
        val metrics: List<NumericMetric>
    ) : CustomFieldStatistic

    data class OptionDistribution(
        override val fieldId: Long,
        override val fieldName: String,
        override val sortOrder: Int,
        val entries: List<DistributionEntry>
    ) : CustomFieldStatistic

    data class Rating(
        override val fieldId: Long,
        override val fieldName: String,
        override val sortOrder: Int,
        val average: String?,
        val distribution: List<DistributionEntry>
    ) : CustomFieldStatistic
}

data class NumericMetric(
    val aggregation: FieldAggregation,
    val value: String,
    val unit: String?
)

data class DistributionEntry(
    val label: String,
    val count: Int
)
