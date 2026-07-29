package com.example.mylibrary.ui.components

fun isValidHalfStarRating(value: Int?): Boolean =
    value == null || value in 1..10

fun nextHalfStarRating(current: Int?, starNumber: Int): Int? {
    require(starNumber in 1..5) { "starNumber must be in 1..5" }
    val lower = (starNumber - 1) * 2
    val half = lower + 1
    val full = lower + 2
    return when (current) {
        half -> full
        full -> lower.takeIf { it > 0 }
        else -> half
    }
}

internal enum class StarFillState {
    EMPTY,
    HALF,
    FULL
}

internal fun starFillState(ratingHalfStars: Int?, starNumber: Int): StarFillState {
    require(starNumber in 1..5) { "starNumber must be in 1..5" }
    val value = ratingHalfStars ?: return StarFillState.EMPTY
    val fullThreshold = starNumber * 2
    val halfThreshold = fullThreshold - 1
    return when {
        value >= fullThreshold -> StarFillState.FULL
        value == halfThreshold -> StarFillState.HALF
        else -> StarFillState.EMPTY
    }
}
