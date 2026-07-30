package com.example.mylibrary.ui.theme.importer

import android.content.Context
import android.content.ContentResolver
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

sealed interface ThemePackageCopyResult {
    data class Success(val bytesCopied: Long) : ThemePackageCopyResult
    data class Failure(val error: ThemePackageError) : ThemePackageCopyResult
}

interface ThemePackageSource {
    suspend fun copyTo(destination: File): ThemePackageCopyResult
}

fun interface ThemePackageSourceFactory {
    fun create(uri: Uri): ThemePackageSource
}

class ContentUriThemePackageSourceFactory(
    private val resolver: ContentResolver
) : ThemePackageSourceFactory {
    constructor(context: Context) : this(
        context.applicationContext.contentResolver
    )

    override fun create(uri: Uri): ThemePackageSource =
        ContentUriThemePackageSource(resolver, uri)
}

class StreamThemePackageSource(
    private val opener: () -> InputStream?
) : ThemePackageSource {
    override suspend fun copyTo(
        destination: File
    ): ThemePackageCopyResult = withContext(Dispatchers.IO) {
        copyThemePackageSource(destination, opener)
    }
}

class ContentUriThemePackageSource(
    private val resolver: ContentResolver,
    private val uri: Uri
) : ThemePackageSource {
    constructor(context: Context, uri: Uri) : this(
        context.applicationContext.contentResolver,
        uri
    )

    override suspend fun copyTo(
        destination: File
    ): ThemePackageCopyResult = withContext(Dispatchers.IO) {
        copyThemePackageSource(destination) {
            resolver.openInputStream(uri)
        }
    }
}

private suspend fun copyThemePackageSource(
    destination: File,
    opener: () -> InputStream?
): ThemePackageCopyResult {
    destination.parentFile?.mkdirs()
    return try {
        val source = opener()
            ?: return ThemePackageCopyResult.Failure(
                ThemePackageError.SourceReadFailed(
                    "The selected source could not be opened"
                )
            )
        var total = 0L
        source.buffered().use { input ->
            FileOutputStream(destination).buffered().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    coroutineContext.ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    total += count
                    if (total > ThemePackageLimits.MAX_SOURCE_ARCHIVE_BYTES) {
                        failThemePackage(
                            ThemePackageError.PackageTooLarge(
                                actualBytes = total,
                                maximumBytes =
                                    ThemePackageLimits.MAX_SOURCE_ARCHIVE_BYTES
                            )
                        )
                    }
                    output.write(buffer, 0, count)
                }
            }
        }
        ThemePackageCopyResult.Success(total)
    } catch (cancelled: CancellationException) {
        destination.delete()
        throw cancelled
    } catch (failure: ThemePackageFailureException) {
        destination.delete()
        ThemePackageCopyResult.Failure(failure.error)
    } catch (exception: Exception) {
        destination.delete()
        ThemePackageCopyResult.Failure(
            ThemePackageError.SourceReadFailed(
                exception.message ?: exception::class.java.simpleName
            )
        )
    }
}
