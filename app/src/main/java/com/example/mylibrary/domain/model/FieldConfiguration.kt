package com.example.mylibrary.domain.model

enum class FieldScope(val storageValue: String) {
    ITEM("item"),
    RECORD("record");

    companion object {
        fun fromStorageValue(value: String): FieldScope =
            entries.firstOrNull { it.storageValue == value } ?: ITEM
    }
}

enum class FieldAggregation(val storageValue: String) {
    SUM("sum"),
    AVERAGE("average"),
    MAXIMUM("maximum"),
    MINIMUM("minimum"),
    OPTION_DISTRIBUTION("option_distribution"),
    RATING_AVERAGE("rating_average"),
    RATING_DISTRIBUTION("rating_distribution");

    companion object {
        fun fromStorageValue(value: String): FieldAggregation? =
            entries.firstOrNull { it.storageValue == value }
    }
}

fun FieldDataType.allowedAggregations(): Set<FieldAggregation> = when (this) {
    FieldDataType.NUMBER -> setOf(
        FieldAggregation.SUM,
        FieldAggregation.AVERAGE,
        FieldAggregation.MAXIMUM,
        FieldAggregation.MINIMUM
    )
    FieldDataType.SINGLE_SELECT,
    FieldDataType.MULTI_SELECT -> setOf(FieldAggregation.OPTION_DISTRIBUTION)
    FieldDataType.RATING -> setOf(
        FieldAggregation.RATING_AVERAGE,
        FieldAggregation.RATING_DISTRIBUTION
    )
    FieldDataType.TEXT,
    FieldDataType.DATE,
    FieldDataType.BOOLEAN -> emptySet()
}

fun Set<FieldAggregation>.compatibleWith(dataType: FieldDataType): Set<FieldAggregation> =
    intersect(dataType.allowedAggregations())
