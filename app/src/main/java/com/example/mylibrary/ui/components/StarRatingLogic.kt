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
    val fraction = starFillFraction(ratingHalfStars, starNumber)
    return when (fraction) {
        1f -> StarFillState.FULL
        0.5f -> StarFillState.HALF
        else -> StarFillState.EMPTY
    }
}

internal fun starFillFraction(
    ratingHalfStars: Int?,
    starNumber: Int
): Float {
    require(starNumber in 1..5) { "starNumber must be in 1..5" }
    val value = ratingHalfStars ?: return 0f
    val lowerBound = (starNumber - 1) * 2
    return ((value - lowerBound) / 2f).coerceIn(0f, 1f)
}

internal fun ratingStarFillFractions(ratingHalfStars: Int?): List<Float> =
    (1..5).map { starFillFraction(ratingHalfStars, it) }
