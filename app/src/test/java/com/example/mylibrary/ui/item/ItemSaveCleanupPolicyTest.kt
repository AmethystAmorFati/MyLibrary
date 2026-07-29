package com.example.mylibrary.ui.item

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemSaveCleanupPolicyTest {
    @Test
    fun databaseSuccessPublishesCompletionBeforeSuccessfulCleanup() = runTest {
        val events = mutableListOf<String>()

        val result = commitPublishAndCleanup(
            commit = {
                events += "database"
                42L
            },
            publishSuccess = { events += "published:$it" },
            cleanup = { events += "old-cover-deleted" },
            onCleanupFailure = { events += "warning" }
        )

        assertEquals(42L, result)
        assertEquals(
            listOf("database", "published:42", "old-cover-deleted"),
            events
        )
    }

    @Test
    fun cleanupFailureDoesNotChangePublishedDatabaseSuccessOrRetryCommit() = runTest {
        var saveCalls = 0
        var completedItemId: Long? = null
        var warnings = 0

        val result = commitPublishAndCleanup(
            commit = {
                saveCalls += 1
                7L
            },
            publishSuccess = { completedItemId = it },
            cleanup = { error("locked old cover") },
            onCleanupFailure = { warnings += 1 }
        )

        assertEquals(7L, result)
        assertEquals(7L, completedItemId)
        assertEquals(1, saveCalls)
        assertEquals(1, warnings)
    }

    @Test
    fun cleanupTargetsOnlyObsoleteCoverAndKeepsCurrentReference() = runTest {
        val sharedOriginal = "images/original/shared.jpg"
        val currentReference = sharedOriginal to "images/thumbnail/new.jpg"
        val obsolete = obsoleteCoverPaths(
            previous = sharedOriginal to "images/thumbnail/old.jpg",
            current = currentReference
        )
        var publishedReference: Pair<String?, String?>? = null
        var deletedReference: Pair<String?, String?>? = null

        commitPublishAndCleanup(
            commit = { currentReference },
            publishSuccess = { publishedReference = it },
            cleanup = { deletedReference = obsolete },
            onCleanupFailure = {}
        )

        assertEquals(currentReference, publishedReference)
        assertEquals(null to "images/thumbnail/old.jpg", deletedReference)
    }

    @Test
    fun databaseFailureDoesNotPublishOrDeleteOldCover() = runTest {
        var published = false
        var cleaned = false

        try {
            commitPublishAndCleanup(
                commit = { error("database failed") },
                publishSuccess = { published = true },
                cleanup = { cleaned = true },
                onCleanupFailure = {}
            )
        } catch (_: IllegalStateException) {
            // Expected.
        }

        assertFalse(published)
        assertFalse(cleaned)
    }

    @Test(expected = CancellationException::class)
    fun cleanupCancellationPropagatesAfterCompletionWasPublished() = runTest {
        var published = false
        try {
            commitPublishAndCleanup(
                commit = { 1L },
                publishSuccess = { published = true },
                cleanup = { throw CancellationException("cancel") },
                onCleanupFailure = {}
            )
        } finally {
            assertTrue(published)
        }
    }
}
