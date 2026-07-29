package com.example.mylibrary.util

import kotlinx.coroutines.CancellationException

/**
 * Runs cleanup that must not change an already committed business result.
 *
 * Cancellation is never downgraded to a warning because it controls coroutine
 * lifetime. Every other failure is reported to the caller and treated as a
 * non-blocking cleanup failure.
 */
internal suspend fun runBestEffortCleanup(
    cleanup: suspend () -> Unit,
    onFailure: (Throwable) -> Unit
): Boolean = try {
    cleanup()
    true
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Throwable) {
    onFailure(error)
    false
}
