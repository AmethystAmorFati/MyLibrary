package com.example.mylibrary.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BestEffortCleanupTest {
    @Test
    fun successfulCleanupReturnsTrueWithoutWarning() = runTest {
        var cleanups = 0
        var warnings = 0

        val successful = runBestEffortCleanup(
            cleanup = { cleanups += 1 },
            onFailure = { warnings += 1 }
        )

        assertTrue(successful)
        assertEquals(1, cleanups)
        assertEquals(0, warnings)
    }

    @Test
    fun ordinaryCleanupFailureIsNonBlockingAndReportedOnce() = runTest {
        val failure = IllegalStateException("cannot delete")
        var reported: Throwable? = null

        val successful = runBestEffortCleanup(
            cleanup = { throw failure },
            onFailure = { reported = it }
        )

        assertFalse(successful)
        assertSame(failure, reported)
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsNeverDowngradedToCleanupWarning() = runTest {
        var warned = false
        try {
            runBestEffortCleanup(
                cleanup = { throw CancellationException("cancel") },
                onFailure = { warned = true }
            )
        } finally {
            assertFalse(warned)
        }
    }
}
