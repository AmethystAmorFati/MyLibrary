package com.example.mylibrary.export.visual

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class ExportFileStore(
    context: Context
) {
    private val appContext = context.applicationContext
    private val temporaryDirectory = File(appContext.cacheDir, "exports/visual")

    suspend fun writeTemporaryPng(
        displayName: String,
        bitmap: Bitmap
    ): File = withContext(Dispatchers.IO) {
        check(temporaryDirectory.mkdirs() || temporaryDirectory.isDirectory) {
            "无法创建导出临时目录"
        }
        val output = File(
            temporaryDirectory,
            "${displayName.removeSuffix(".png")}-${UUID.randomUUID()}.png"
        )
        val staging = File(output.parentFile, "${output.name}.tmp")
        var committed = false
        try {
            coroutineContext.ensureActive()
            FileOutputStream(staging).use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    "PNG 编码失败"
                }
            }
            coroutineContext.ensureActive()
            check(staging.isFile && staging.length() > 0L) {
                "PNG 编码未生成有效文件"
            }
            check(staging.renameTo(output)) { "导出临时文件提交失败" }
            committed = true
            output
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(TAG, "Temporary PNG encoding failed", error)
            throw VisualExportException(
                VisualExportError.RENDER_FAILED,
                "生成失败，请重试",
                error
            )
        } finally {
            if (!committed) {
                staging.delete()
                output.delete()
            }
        }
    }

    suspend fun saveToUri(
        source: File,
        destination: Uri,
        fallbackDisplayName: String
    ): SavedVisualExportLocation =
        withContext(Dispatchers.IO) {
            var committed = false
            try {
                appContext.contentResolver.openOutputStream(destination, "w")
                    ?.use { output ->
                        source.inputStream().buffered().use { input ->
                            input.copyToCancellable(output)
                        }
                    }
                    ?: error("无法打开保存位置")
                committed = true
                SavedVisualExportLocation(
                    displayName = runCatching {
                        queryDisplayName(destination)
                    }.getOrNull() ?: fallbackDisplayName,
                    displayLocation = null
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(TAG, "SAF image save failed", error)
                throw VisualExportException(
                    VisualExportError.SAVE_FAILED,
                    "保存失败",
                    error
                )
            } finally {
                if (!committed) {
                    deleteUriQuietly(destination)
                }
            }
        }

    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun saveToPictures(
        source: File,
        requestedName: String
    ): SavedVisualExportLocation = withContext(Dispatchers.IO) {
        val resolver = appContext.contentResolver
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val relativePath = "${Environment.DIRECTORY_PICTURES}/MyLibrary/"
        val displayName = uniqueMediaStoreName(requestedName, relativePath)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values)
            ?: throw VisualExportException(
                VisualExportError.SAVE_FAILED,
                "保存失败"
            )
        var published = false
        try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                source.inputStream().buffered().use { input ->
                    input.copyToCancellable(output)
                }
            } ?: error("无法写入系统图片")
            check(
                resolver.update(
                    uri,
                    ContentValues().apply {
                        put(MediaStore.Images.Media.IS_PENDING, 0)
                    },
                    null,
                    null
                ) == 1
            ) { "无法提交系统图片" }
            published = true
            SavedVisualExportLocation(
                displayName = displayName,
                displayLocation = "Pictures/MyLibrary"
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(TAG, "MediaStore image save failed", error)
            throw VisualExportException(
                VisualExportError.SAVE_FAILED,
                "保存失败",
                error
            )
        } finally {
            if (!published) deleteUriQuietly(uri)
        }
    }

    fun deleteTemporary(file: File?) {
        file?.takeIf { it.parentFile == temporaryDirectory }?.delete()
    }

    private fun deleteUriQuietly(uri: Uri) {
        try {
            appContext.contentResolver.delete(uri, null, null)
        } catch (_: Exception) {
            // Cleanup is best-effort and must not replace the primary failure.
            Log.w(TAG, "Unable to remove incomplete export Uri: $uri")
        }
    }

    private fun queryDisplayName(uri: Uri): String? =
        appContext.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            index.takeIf { it >= 0 }?.let(cursor::getString)
        }?.takeIf(String::isNotBlank)

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun uniqueMediaStoreName(
        requestedName: String,
        relativePath: String
    ): String {
        val resolver = appContext.contentResolver
        var sequence = 1
        while (sequence < 10_000) {
            val candidate = ExportFileNames.withSequence(requestedName, sequence)
            val exists = resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.Images.Media.RELATIVE_PATH}=? AND " +
                    "${MediaStore.Images.Media.DISPLAY_NAME}=?",
                arrayOf(relativePath, candidate),
                null
            )?.use { it.moveToFirst() } == true
            if (!exists) return candidate
            sequence += 1
        }
        throw VisualExportException(
            VisualExportError.SAVE_FAILED,
            "保存失败"
        )
    }

    private suspend fun java.io.InputStream.copyToCancellable(
        output: java.io.OutputStream
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            coroutineContext.ensureActive()
            val count = read(buffer)
            if (count < 0) break
            if (count > 0) output.write(buffer, 0, count)
        }
    }

    private companion object {
        const val TAG = "ExportFileStore"
    }
}
