package com.example.mylibrary.ui.components

import android.content.Context
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mylibrary.data.image.CoverImageProcessor
import com.example.mylibrary.data.image.resolveStoredCoverFile
import com.example.mylibrary.domain.model.ItemTypeKind
import com.example.mylibrary.ui.theme.AppTheme
import com.example.mylibrary.ui.theme.AppImageScrim
import com.example.mylibrary.ui.theme.HairlineWidth
import com.example.mylibrary.ui.theme.LibraryShapes
import com.example.mylibrary.ui.theme.SurfaceRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore

enum class CoverDisplayMode {
    CALENDAR,
    TIMELINE,
    LIBRARY_GRID,
    LIBRARY_LIST,
    LIBRARY_COVER_ONLY,
    DETAIL,
    POSTER,
    EDITOR
}

data class CoverVisualPolicy(
    val showsSurface: Boolean,
    val showsBorder: Boolean,
    val showsPlaceholder: Boolean,
    val showsPlaceholderText: Boolean
)

internal fun coverVisualPolicy(displayMode: CoverDisplayMode): CoverVisualPolicy =
    when (displayMode) {
        CoverDisplayMode.CALENDAR -> CoverVisualPolicy(
            showsSurface = false,
            showsBorder = false,
            showsPlaceholder = false,
            showsPlaceholderText = false
        )
        CoverDisplayMode.TIMELINE,
        CoverDisplayMode.LIBRARY_GRID,
        CoverDisplayMode.LIBRARY_LIST -> CoverVisualPolicy(
            showsSurface = true,
            showsBorder = false,
            showsPlaceholder = true,
            showsPlaceholderText = true
        )
        CoverDisplayMode.LIBRARY_COVER_ONLY -> CoverVisualPolicy(
            showsSurface = true,
            showsBorder = false,
            showsPlaceholder = true,
            showsPlaceholderText = false
        )
        CoverDisplayMode.DETAIL,
        CoverDisplayMode.POSTER,
        CoverDisplayMode.EDITOR -> CoverVisualPolicy(
            showsSurface = true,
            showsBorder = true,
            showsPlaceholder = true,
            showsPlaceholderText = true
        )
    }

@Composable
fun CoverImage(
    thumbnailPath: String?,
    originalPath: String? = null,
    title: String,
    creator: String = "",
    typeName: String,
    typeId: Long? = null,
    displayMode: CoverDisplayMode,
    modifier: Modifier = Modifier,
    shape: Shape = LibraryShapes.small
) {
    val visualPolicy = coverVisualPolicy(displayMode)
    val context = LocalContext.current
    val candidates = remember(
        thumbnailPath,
        originalPath,
        displayMode
    ) {
        if (displayMode.prefersOriginal) {
            listOf(originalPath, thumbnailPath)
        } else {
            listOf(thumbnailPath, originalPath)
        }.filterNotNull().filter(String::isNotBlank).distinct()
    }
    val maxDecodeEdge = coverDecodeEdge(displayMode)
    val cachedBitmap = CoverBitmapCache.peek(candidates)
    val bitmap by produceState<ImageBitmap?>(
        initialValue = cachedBitmap,
        key1 = candidates,
        key2 = maxDecodeEdge
    ) {
        value = CoverBitmapCache.load(
            context = context.applicationContext,
            candidates = candidates,
            maxEdge = maxDecodeEdge
        )
    }
    val sizingModifier = if (displayMode == CoverDisplayMode.DETAIL) {
        Modifier.aspectRatio(DetailCoverAspectRatio)
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .then(sizingModifier)
            .clip(shape)
            .then(
                if (visualPolicy.showsSurface) {
                    Modifier.background(
                        AppTheme.surface(SurfaceRole.CARD).fallbackColor.let {
                            if (displayMode == CoverDisplayMode.DETAIL) {
                                it.copy(alpha = 1f)
                            } else {
                                it
                            }
                        }
                    )
                } else {
                    Modifier
                }
            )
            .then(
                if (visualPolicy.showsBorder) {
                    Modifier.border(HairlineWidth, AppTheme.colors.border, shape)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        bitmap?.let {
            if (displayMode == CoverDisplayMode.POSTER) {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.32f
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(AppImageScrim.copy(alpha = 0.18f))
                )
            }
            Image(
                bitmap = it,
                contentDescription = "《$title》封面",
                modifier = Modifier.fillMaxSize(),
                contentScale = displayMode.contentScale
            )
        }
        if (bitmap == null && visualPolicy.showsPlaceholder) {
            DefaultCover(
                title = title,
                creator = creator,
                typeName = typeName,
                typeId = typeId,
                compact = displayMode == CoverDisplayMode.TIMELINE,
                showText = visualPolicy.showsPlaceholderText
            )
        }
    }
}

private data class CachedCoverBitmap(
    val bitmap: ImageBitmap,
    val decodedForEdge: Int
)

private object CoverBitmapCache {
    private const val MAX_CONCURRENT_DECODES = 2
    private val lock = Any()
    private val cache = object : LruCache<String, CachedCoverBitmap>(32 * 1024) {
        override fun sizeOf(key: String, value: CachedCoverBitmap): Int =
            (value.bitmap.width * value.bitmap.height * 4 / 1024).coerceAtLeast(1)
    }
    private val loadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val loadCoordinator = CoverLoadCoordinator<ImageBitmap?>(
        scope = loadScope,
        maxConcurrentLoads = MAX_CONCURRENT_DECODES
    )

    fun peek(candidates: List<String>): ImageBitmap? =
        synchronized(lock) {
            candidates.firstNotNullOfOrNull { path -> cache.get(path)?.bitmap }
        }

    fun clear() {
        synchronized(lock) {
            cache.evictAll()
        }
    }

    suspend fun load(
        context: Context,
        candidates: List<String>,
        maxEdge: Int
    ): ImageBitmap? {
        if (candidates.isEmpty()) return null
        peekSufficient(candidates.first(), maxEdge)?.let { return it }

        val requestKey = coverRequestKey(candidates, maxEdge)
        val result = loadCoordinator.load(requestKey) {
            decodeFirstAvailable(context, candidates, maxEdge)
        }
        return result ?: peek(candidates)
    }

    private fun decodeFirstAvailable(
        context: Context,
        candidates: List<String>,
        maxEdge: Int
    ): ImageBitmap? {
        candidates.forEach { path ->
            peekSufficient(path, maxEdge)?.let { return it }
            val file = resolveStoredCoverFile(context, path) ?: return@forEach
            val decoded = runCatching {
                CoverImageProcessor.decodeSampledFile(file, maxEdge)?.asImageBitmap()
            }.getOrNull() ?: return@forEach
            put(path, decoded, maxEdge)
            return decoded
        }
        return null
    }

    private fun peekSufficient(path: String, maxEdge: Int): ImageBitmap? =
        synchronized(lock) {
            cache.get(path)
                ?.takeIf { coverCacheCanSatisfy(it.decodedForEdge, maxEdge) }
                ?.bitmap
        }

    private fun put(path: String, bitmap: ImageBitmap, maxEdge: Int) {
        synchronized(lock) {
            val current = cache.get(path)
            if (current == null || current.decodedForEdge < maxEdge) {
                cache.put(
                    path,
                    CachedCoverBitmap(
                        bitmap = bitmap,
                        decodedForEdge = maxEdge
                    )
                )
            }
        }
    }
}

fun clearCoverImageMemoryCache() {
    CoverBitmapCache.clear()
}

internal fun coverRequestKey(candidates: List<String>, maxEdge: Int): String =
    candidates.joinToString(separator = "\u001F", postfix = ":$maxEdge")

internal fun coverCacheCanSatisfy(cachedEdge: Int, requestedEdge: Int): Boolean =
    cachedEdge >= requestedEdge

internal class CoverLoadCoordinator<T>(
    private val scope: CoroutineScope,
    maxConcurrentLoads: Int
) {
    private val lock = Any()
    private val inFlight = mutableMapOf<String, Deferred<T>>()
    private val semaphore = Semaphore(maxConcurrentLoads)

    suspend fun load(key: String, loader: suspend () -> T): T {
        val request = synchronized(lock) {
            inFlight[key] ?: scope.async<T> {
                semaphore.acquire()
                try {
                    loader()
                } finally {
                    semaphore.release()
                }
            }.also { deferred ->
                inFlight[key] = deferred
                deferred.invokeOnCompletion {
                    synchronized(lock) {
                        if (inFlight[key] === deferred) {
                            inFlight.remove(key)
                        }
                    }
                }
            }
        }
        return request.await()
    }
}

@Composable
private fun DefaultCover(
    title: String,
    creator: String,
    typeName: String,
    typeId: Long?,
    compact: Boolean,
    showText: Boolean
) {
    val isMovie = typeId?.let {
        ItemTypeKind.fromTypeId(it) == ItemTypeKind.MOVIE
    } ?: (
        typeName.equals("Movie", ignoreCase = true) ||
            typeName.contains("电影")
        )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(if (compact) 4.dp else 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Icon(
            imageVector = if (isMovie) {
                Icons.Outlined.Movie
            } else {
                Icons.AutoMirrored.Outlined.MenuBook
            },
            contentDescription = null,
            modifier = Modifier.size(if (compact) 14.dp else 22.dp),
            tint = AppTheme.colors.textSecondary
        )
        if (showText) {
            Text(
                text = title.ifBlank { if (isMovie) "MOVIE" else "BOOK" },
                color = AppTheme.colors.textPrimary,
                style = AppTheme.typography.metadata,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            if (!compact && creator.isNotBlank()) {
                Text(
                    text = creator,
                    color = AppTheme.colors.textSecondary,
                    style = AppTheme.typography.metadata,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private val CoverDisplayMode.prefersOriginal: Boolean
    get() = this == CoverDisplayMode.DETAIL || this == CoverDisplayMode.POSTER

private val CoverDisplayMode.contentScale: ContentScale
    get() = when (this) {
        CoverDisplayMode.DETAIL,
        CoverDisplayMode.POSTER -> ContentScale.Fit
        else -> ContentScale.Crop
    }

internal fun coverDecodeEdge(displayMode: CoverDisplayMode): Int =
    when (displayMode) {
        CoverDisplayMode.CALENDAR -> 160
        CoverDisplayMode.TIMELINE -> 192
        CoverDisplayMode.LIBRARY_COVER_ONLY,
        CoverDisplayMode.LIBRARY_LIST,
        CoverDisplayMode.LIBRARY_GRID,
        CoverDisplayMode.EDITOR -> 480
        CoverDisplayMode.DETAIL -> 1_600
        CoverDisplayMode.POSTER -> 2_048
    }

internal const val DetailCoverAspectRatio = 2f / 3f
