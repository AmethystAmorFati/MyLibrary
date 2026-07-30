package com.example.mylibrary.domain.model

/**
 * Shared stable ordering used by the home calendar and static calendar export.
 */
fun <T> orderedDistinctActivityCovers(
    activities: List<T>,
    recordCreatedAt: (T) -> Long,
    recordId: (T) -> Long?,
    activityId: (T) -> Long,
    itemId: (T) -> Long,
    limit: Int = 4
): List<T> {
    require(limit >= 0)
    return activities
        .sortedWith(
            compareByDescending(recordCreatedAt)
                .thenByDescending { recordId(it) ?: Long.MIN_VALUE }
                .thenByDescending(activityId)
        )
        .distinctBy(itemId)
        .take(limit)
}
