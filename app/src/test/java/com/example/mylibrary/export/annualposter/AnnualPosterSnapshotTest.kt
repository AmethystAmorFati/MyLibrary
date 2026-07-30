package com.example.mylibrary.export.annualposter

import com.example.mylibrary.domain.model.ItemTypeKind
import com.example.mylibrary.export.visual.AnnualPosterCategory
import com.example.mylibrary.export.visual.VisualExportActivity
import com.example.mylibrary.export.visual.annualPosterNoDataMessage
import com.example.mylibrary.util.toStartOfDayMillis
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnualPosterSnapshotTest {
    @Test
    fun allBookAndMovieCategoriesUseOnlyStableFixedTypeIds() {
        val input = listOf(
            activity(1, itemId = 1, typeId = ItemTypeKind.BOOK_TYPE_ID),
            activity(2, itemId = 2, typeId = ItemTypeKind.MOVIE_TYPE_ID),
            activity(3, itemId = 3, typeId = 99)
        )

        assertEquals(
            listOf(1L, 2L),
            snapshot(AnnualPosterCategory.ALL, input).items.map { it.itemId }
        )
        assertEquals(
            listOf(1L),
            snapshot(AnnualPosterCategory.BOOK, input).items.map { it.itemId }
        )
        assertEquals(
            listOf(2L),
            snapshot(AnnualPosterCategory.MOVIE, input).items.map { it.itemId }
        )
    }

    @Test
    fun repeatedRecordsForAnItemProduceOneCoverUsingFirstAnnualActivity() {
        val input = listOf(
            activity(3, itemId = 7, day = 20, createdAt = 300),
            activity(1, itemId = 7, day = 2, createdAt = 200),
            activity(2, itemId = 7, day = 2, createdAt = 100)
        )

        val item = snapshot(AnnualPosterCategory.ALL, input).items.single()

        assertEquals(7L, item.itemId)
        assertEquals(LocalDate.of(2026, 1, 2).toStartOfDayMillis(), item.firstActivityDate)
        assertEquals(100L, item.firstRecordCreatedAt)
    }

    @Test
    fun orderUsesFirstDateThenRecordCreationThenItemIdAndIgnoresInputOrder() {
        val input = listOf(
            activity(4, itemId = 40, day = 2, createdAt = 10),
            activity(3, itemId = 30, day = 1, createdAt = 30),
            activity(2, itemId = 20, day = 1, createdAt = 20),
            activity(1, itemId = 10, day = 1, createdAt = 20)
        )

        val expected = listOf(10L, 20L, 30L, 40L)
        assertEquals(
            expected,
            snapshot(AnnualPosterCategory.ALL, input).items.map { it.itemId }
        )
        assertEquals(
            expected,
            snapshot(AnnualPosterCategory.ALL, input.reversed()).items.map { it.itemId }
        )
    }

    @Test
    fun validOriginalIsPreferredAndValidThumbnailIsTheFallback() {
        val unresolved = snapshot(
            AnnualPosterCategory.BOOK,
            listOf(
                activity(
                    1,
                    itemId = 8,
                    coverPath = "valid-original.webp",
                    thumbnailPath = "valid-thumb.webp"
                ),
                activity(
                    2,
                    itemId = 9,
                    coverPath = "broken-original.webp",
                    thumbnailPath = "valid-fallback.webp"
                )
            )
        )
        val resolved = resolveAnnualPosterCoverPaths(unresolved) {
            it.takeIf { path -> path.startsWith("valid") }
                ?.let { path -> AnnualPosterCoverMetadata(path, 600, 900) }
        }

        assertEquals(
            listOf("valid-original.webp", "valid-fallback.webp"),
            resolved.items.map { it.resolvedCoverPath }
        )
        assertEquals(listOf(600, 600), resolved.items.map { it.resolvedCoverWidth })
        assertEquals(listOf(900, 900), resolved.items.map { it.resolvedCoverHeight })
        assertEquals(
            listOf(2.0 / 3.0, 2.0 / 3.0),
            resolved.items.map { it.resolvedAspectRatio }
        )
    }

    @Test
    fun missingAndBrokenCoverCandidatesAreFilteredBeforeLayout() {
        val unresolved = snapshot(
            AnnualPosterCategory.BOOK,
            listOf(
                activity(1, itemId = 1, coverPath = null, thumbnailPath = null),
                activity(
                    2,
                    itemId = 2,
                    coverPath = "broken-original.webp",
                    thumbnailPath = "broken-thumb.webp"
                ),
                activity(
                    3,
                    itemId = 3,
                    coverPath = "valid.webp",
                    thumbnailPath = null
                )
            )
        )
        val resolved = resolveAnnualPosterCoverPaths(unresolved) {
            it.takeIf { path -> path == "valid.webp" }
                ?.let { path -> AnnualPosterCoverMetadata(path, 800, 1_200) }
        }

        assertEquals(listOf(3L), resolved.items.map { it.itemId })
        assertEquals(1, annualPosterLayout(resolved.items).cells.size)
        assertTrue(resolved.items.all { it.resolvedCoverPath != null })
    }

    @Test
    fun filteringPreservesThePreviouslyResolvedStableOrder() {
        val unresolved = snapshot(
            AnnualPosterCategory.ALL,
            listOf(
                activity(3, itemId = 30, day = 3, coverPath = "valid-30"),
                activity(1, itemId = 10, day = 1, coverPath = "valid-10"),
                activity(2, itemId = 20, day = 2, coverPath = "broken-20")
            )
        )

        val resolved = resolveAnnualPosterCoverPaths(unresolved) {
            it.takeIf { path -> path.startsWith("valid") }
                ?.let { path -> AnnualPosterCoverMetadata(path, 600, 900) }
        }

        assertEquals(listOf(10L, 30L), resolved.items.map { it.itemId })
    }

    @Test
    fun allInvalidCoversProduceAnEmptyResolvedSnapshot() {
        val unresolved = snapshot(
            AnnualPosterCategory.MOVIE,
            listOf(
                activity(
                    1,
                    itemId = 8,
                    typeId = ItemTypeKind.MOVIE_TYPE_ID,
                    coverPath = "broken",
                    thumbnailPath = null
                )
            )
        )

        assertTrue(
            resolveAnnualPosterCoverPaths(unresolved) { null }
                .items
                .isEmpty()
        )
    }

    @Test
    fun emptyResolvedCategoriesUseEffectiveCoverMessages() {
        assertEquals(
            "该年份没有有效封面",
            annualPosterNoDataMessage(AnnualPosterCategory.ALL)
        )
        assertEquals(
            "该年份没有有效书籍封面",
            annualPosterNoDataMessage(AnnualPosterCategory.BOOK)
        )
        assertEquals(
            "该年份没有有效电影封面",
            annualPosterNoDataMessage(AnnualPosterCategory.MOVIE)
        )
    }

    private fun snapshot(
        category: AnnualPosterCategory,
        activities: List<VisualExportActivity>
    ) = buildAnnualPosterSnapshot(2026, category, activities)

    private fun activity(
        id: Long,
        itemId: Long,
        typeId: Long = ItemTypeKind.BOOK_TYPE_ID,
        day: Int = id.toInt(),
        createdAt: Long = id,
        coverPath: String? = "cover-$itemId.webp",
        thumbnailPath: String? = "thumb-$itemId.webp"
    ) = VisualExportActivity(
        activityId = id,
        date = LocalDate.of(2026, 1, day).toStartOfDayMillis(),
        itemId = itemId,
        typeId = typeId,
        recordId = id,
        recordCreatedAt = createdAt,
        title = "Item $itemId",
        coverPath = coverPath,
        thumbnailPath = thumbnailPath
    )
}
