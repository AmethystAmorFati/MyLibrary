package com.example.mylibrary.export.annualposter

import com.example.mylibrary.domain.model.ItemTypeKind
import com.example.mylibrary.export.visual.AnnualPosterCategory
import com.example.mylibrary.export.visual.VisualExportActivity

data class AnnualPosterItem(
    val itemId: Long,
    val typeId: Long,
    val title: String,
    val coverPath: String?,
    val thumbnailPath: String?,
    val firstActivityDate: Long,
    val firstRecordCreatedAt: Long,
    val resolvedCoverPath: String? = null,
    val resolvedCoverWidth: Int? = null,
    val resolvedCoverHeight: Int? = null
) {
    val resolvedAspectRatio: Double?
        get() {
            val width = resolvedCoverWidth ?: return null
            val height = resolvedCoverHeight ?: return null
            return if (width > 0 && height > 0) {
                width.toDouble() / height.toDouble()
            } else {
                null
            }
        }
}

data class AnnualPosterCoverMetadata(
    val path: String,
    val width: Int,
    val height: Int
) {
    init {
        require(path.isNotBlank())
        require(width > 0)
        require(height > 0)
    }
}

data class AnnualPosterSnapshot(
    val year: Int,
    val category: AnnualPosterCategory,
    val items: List<AnnualPosterItem>
)

fun buildAnnualPosterSnapshot(
    year: Int,
    category: AnnualPosterCategory,
    activities: List<VisualExportActivity>
): AnnualPosterSnapshot {
    val acceptedTypeIds = when (category) {
        AnnualPosterCategory.ALL -> setOf(
            ItemTypeKind.BOOK_TYPE_ID,
            ItemTypeKind.MOVIE_TYPE_ID
        )
        AnnualPosterCategory.BOOK -> setOf(ItemTypeKind.BOOK_TYPE_ID)
        AnnualPosterCategory.MOVIE -> setOf(ItemTypeKind.MOVIE_TYPE_ID)
    }
    val firstByItem = activities
        .asSequence()
        .filter { it.typeId in acceptedTypeIds }
        .groupBy(VisualExportActivity::itemId)
        .mapValues { (_, itemActivities) ->
            itemActivities.minWith(
                compareBy<VisualExportActivity> { it.date }
                    .thenBy { it.recordCreatedAt }
                    .thenBy { it.recordId ?: Long.MIN_VALUE }
                    .thenBy { it.activityId }
            )
        }
    val items = firstByItem.values
        .map { activity ->
            AnnualPosterItem(
                itemId = activity.itemId,
                typeId = activity.typeId,
                title = activity.title,
                coverPath = activity.coverPath,
                thumbnailPath = activity.thumbnailPath,
                firstActivityDate = activity.date,
                firstRecordCreatedAt = activity.recordCreatedAt
            )
        }
        .sortedWith(
            compareBy<AnnualPosterItem> { it.firstActivityDate }
                .thenBy { it.firstRecordCreatedAt }
                .thenBy { it.itemId }
        )
    return AnnualPosterSnapshot(year, category, items)
}

fun resolveAnnualPosterCoverPaths(
    snapshot: AnnualPosterSnapshot,
    resolveCover: (String) -> AnnualPosterCoverMetadata?
): AnnualPosterSnapshot = snapshot.copy(
    items = snapshot.items.mapNotNull { item ->
        listOf(item.coverPath, item.thumbnailPath)
            .filterNotNull()
            .filter(String::isNotBlank)
            .distinct()
            .firstNotNullOfOrNull(resolveCover)
            ?.let { cover ->
                item.copy(
                    resolvedCoverPath = cover.path,
                    resolvedCoverWidth = cover.width,
                    resolvedCoverHeight = cover.height
                )
            }
    }
)
