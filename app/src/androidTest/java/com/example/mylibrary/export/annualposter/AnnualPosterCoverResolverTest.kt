package com.example.mylibrary.export.annualposter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mylibrary.domain.model.ItemTypeKind
import com.example.mylibrary.export.visual.AnnualPosterCategory
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnnualPosterCoverResolverTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun validOriginalAndThumbnailFallbackSurviveWhileMissingAndBrokenAreFiltered() =
        runBlocking {
            val directory = File(
                context.filesDir,
                "images/annual-resolver-${System.nanoTime()}"
            )
            directory.mkdirs()
            val validOriginal = File(directory, "valid-original.png")
            val validThumbnail = File(directory, "valid-thumbnail.png")
            val brokenOriginal = File(directory, "broken-original.png")
            val brokenThumbnail = File(directory, "broken-thumbnail.png")
            writePng(validOriginal, Color.RED)
            writePng(validThumbnail, Color.BLUE)
            brokenOriginal.writeBytes(byteArrayOf(1, 2, 3))
            brokenThumbnail.writeBytes(byteArrayOf(4, 5, 6))

            try {
                val snapshot = AnnualPosterSnapshot(
                    year = 2026,
                    category = AnnualPosterCategory.BOOK,
                    items = listOf(
                        item(1, relative(validOriginal), relative(validThumbnail)),
                        item(2, relative(brokenOriginal), relative(validThumbnail)),
                        item(3, "images/missing.png", null),
                        item(4, relative(brokenOriginal), relative(brokenThumbnail))
                    )
                )

                val resolved = resolveAnnualPosterCovers(context, snapshot)

                assertEquals(listOf(1L, 2L), resolved.items.map { it.itemId })
                assertEquals(
                    listOf(relative(validOriginal), relative(validThumbnail)),
                    resolved.items.map { it.resolvedCoverPath }
                )
                assertEquals(listOf(24, 24), resolved.items.map { it.resolvedCoverWidth })
                assertEquals(listOf(36, 36), resolved.items.map { it.resolvedCoverHeight })
            } finally {
                directory.deleteRecursively()
            }
        }

    private fun item(
        id: Long,
        coverPath: String?,
        thumbnailPath: String?
    ) = AnnualPosterItem(
        itemId = id,
        typeId = ItemTypeKind.BOOK_TYPE_ID,
        title = "Item $id",
        coverPath = coverPath,
        thumbnailPath = thumbnailPath,
        firstActivityDate = id,
        firstRecordCreatedAt = id
    )

    private fun relative(file: File): String =
        file.relativeTo(context.filesDir).invariantSeparatorsPath

    private fun writePng(file: File, color: Int) {
        val bitmap = Bitmap.createBitmap(24, 36, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(color)
            FileOutputStream(file).use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            bitmap.recycle()
        }
    }
}
