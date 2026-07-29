package com.example.mylibrary.ui.components

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverImageCacheTest {
    @Test
    fun largerCachedThumbnailCanServeASmallerRequest() {
        assertTrue(coverCacheCanSatisfy(cachedEdge = 480, requestedEdge = 160))
        assertTrue(coverCacheCanSatisfy(cachedEdge = 480, requestedEdge = 480))
        assertFalse(coverCacheCanSatisfy(cachedEdge = 160, requestedEdge = 480))
    }

    @Test
    fun requestKeyIncludesCandidatesAndDecodeBucket() {
        val paths = listOf("covers/one.webp", "covers/one_original.webp")

        assertEquals(
            coverRequestKey(paths, 480),
            coverRequestKey(paths, 480)
        )
        assertFalse(coverRequestKey(paths, 160) == coverRequestKey(paths, 480))
        assertFalse(
            coverRequestKey(paths.reversed(), 480) ==
                coverRequestKey(paths, 480)
        )
    }

    @Test
    fun sameKeySharesOneInFlightLoad() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val coordinator = CoverLoadCoordinator<Int>(
            scope = scope,
            maxConcurrentLoads = 2
        )
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val loadCount = AtomicInteger(0)

        try {
            val first = async {
                coordinator.load("same-key") {
                    loadCount.incrementAndGet()
                    started.complete(Unit)
                    release.await()
                    7
                }
            }
            started.await()
            val second = async(start = CoroutineStart.UNDISPATCHED) {
                coordinator.load("same-key") {
                    loadCount.incrementAndGet()
                    9
                }
            }

            release.complete(Unit)

            assertEquals(7, first.await())
            assertEquals(7, second.await())
            assertEquals(1, loadCount.get())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun differentKeysCanDecodeConcurrentlyWithinTheBound() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val coordinator = CoverLoadCoordinator<Int>(
            scope = scope,
            maxConcurrentLoads = 2
        )
        val release = CompletableDeferred<Unit>()
        val bothStarted = CompletableDeferred<Unit>()
        val active = AtomicInteger(0)
        val maximumActive = AtomicInteger(0)

        suspend fun loadValue(value: Int): Int {
            val nowActive = active.incrementAndGet()
            maximumActive.updateAndGet { current -> maxOf(current, nowActive) }
            if (nowActive == 2) bothStarted.complete(Unit)
            return try {
                release.await()
                value
            } finally {
                active.decrementAndGet()
            }
        }

        try {
            val first = async { coordinator.load("first") { loadValue(1) } }
            val second = async { coordinator.load("second") { loadValue(2) } }
            val third = async { coordinator.load("third") { loadValue(3) } }

            withTimeout(2_000) { bothStarted.await() }
            release.complete(Unit)

            assertEquals(1, first.await())
            assertEquals(2, second.await())
            assertEquals(3, third.await())
            assertEquals(2, maximumActive.get())
        } finally {
            scope.cancel()
        }
    }
}
