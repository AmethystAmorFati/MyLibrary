package com.example.mylibrary.export.report

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.mylibrary.export.visual.ExportFileNames
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class ReportFileStore(context: Context) {
    private val appContext = context.applicationContext
    private val temporaryDirectory = File(appContext.cacheDir, "exports/reports")

    suspend fun writeTemporaryPng(
        displayName: String,
        bitmap: android.graphics.Bitmap
    ): File = withContext(Dispatchers.IO) {
        ensureTemporaryDirectory()
        val output = File(
            temporaryDirectory,
            "${displayName.removeSuffix(".png")}-${UUID.randomUUID()}.png"
        )
        val staging = File(output.parentFile, "${output.name}.tmp")
        var committed = false
        try {
            FileOutputStream(staging).use {
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) {
                    "PNG 编码失败"
                }
            }
            coroutineContext.ensureActive()
            check(staging.isFile && staging.length() > 0L)
            check(staging.renameTo(output))
            committed = true
            output
        } finally {
            if (!committed) {
                staging.delete()
                output.delete()
            }
        }
    }

    fun newTemporaryPdf(displayName: String): File {
        ensureTemporaryDirectory()
        return File(
            temporaryDirectory,
            "${displayName.removeSuffix(".pdf")}-${UUID.randomUUID()}.pdf"
        )
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun savePngBatchToPictures(
        files: List<File>,
        requestedNames: List<String>
    ): SavedExportLocation = withContext(Dispatchers.IO) {
        require(files.isNotEmpty() && files.size == requestedNames.size)
        val resolver = appContext.contentResolver
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val relativePath = "${Environment.DIRECTORY_PICTURES}/MyLibrary/"
        val sequence = findBatchSequence(collection, relativePath, requestedNames)
        val displayNames = requestedNames.map {
            ExportFileNames.withSequence(it, sequence)
        }
        val created = mutableListOf<Uri>()
        try {
            files.zip(displayNames).forEach { (file, displayName) ->
                coroutineContext.ensureActive()
                val uri = resolver.insert(
                    collection,
                    ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                ) ?: error("无法创建报告图片")
                created += uri
                resolver.openOutputStream(uri, "w")?.use { output ->
                    file.inputStream().buffered().use { input ->
                        input.copyToCancellable(output)
                    }
                } ?: error("无法写入报告图片")
            }
            created.forEach { uri ->
                check(
                    resolver.update(
                        uri,
                        ContentValues().apply {
                            put(MediaStore.Images.Media.IS_PENDING, 0)
                        },
                        null,
                        null
                    ) == 1
                )
            }
            SavedExportLocation(
                displayName = displayNames.first(),
                destination = ExportDestination.PICTURES,
                displayLocation = "Pictures/MyLibrary",
                fileCount = displayNames.size
            )
        } catch (cancelled: CancellationException) {
            created.forEach(::deleteUriQuietly)
            throw cancelled
        } catch (error: Exception) {
            created.forEach(::deleteUriQuietly)
            throw ReportExportException(
                ReportExportError.SAVE_FAILED,
                "保存失败",
                error
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun savePdfToDownloads(
        file: File,
        requestedName: String
    ): SavedExportLocation = withContext(Dispatchers.IO) {
        val resolver = appContext.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/MyLibrary/"
        val sequence = findBatchSequence(collection, relativePath, listOf(requestedName))
        val displayName = ExportFileNames.withSequence(requestedName, sequence)
        val uri = resolver.insert(
            collection,
            ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        ) ?: throw ReportExportException(ReportExportError.SAVE_FAILED, "保存失败")
        var published = false
        try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                file.inputStream().buffered().use { input ->
                    input.copyToCancellable(output)
                }
            } ?: error("无法写入 PDF")
            check(
                resolver.update(
                    uri,
                    ContentValues().apply {
                        put(MediaStore.Downloads.IS_PENDING, 0)
                    },
                    null,
                    null
                ) == 1
            )
            published = true
            SavedExportLocation(
                displayName = displayName,
                destination = ExportDestination.DOWNLOADS,
                displayLocation = "Download/MyLibrary"
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            throw ReportExportException(
                ReportExportError.SAVE_FAILED,
                "保存失败",
                error
            )
        } finally {
            if (!published) deleteUriQuietly(uri)
        }
    }

    suspend fun savePngBatchToTree(
        files: List<File>,
        requestedNames: List<String>,
        treeUri: Uri
    ): SavedExportLocation = withContext(Dispatchers.IO) {
        require(files.isNotEmpty() && files.size == requestedNames.size)
        val resolver = appContext.contentResolver
        val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val parent = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            treeDocumentId
        )
        val existingNames = queryTreeNames(treeUri, treeDocumentId)
        val sequence = firstAvailableSequence(existingNames, requestedNames)
        val names = requestedNames.map { ExportFileNames.withSequence(it, sequence) }
        val created = mutableListOf<Uri>()
        try {
            files.zip(names).forEach { (file, name) ->
                coroutineContext.ensureActive()
                val uri = DocumentsContract.createDocument(
                    resolver,
                    parent,
                    "image/png",
                    name
                ) ?: error("无法创建报告图片")
                created += uri
                resolver.openOutputStream(uri, "w")?.use { output ->
                    file.inputStream().buffered().use { input ->
                        input.copyToCancellable(output)
                    }
                } ?: error("无法写入报告图片")
            }
            SavedExportLocation(
                displayName = names.first(),
                destination = ExportDestination.USER_SELECTED,
                displayLocation = null,
                fileCount = names.size
            )
        } catch (cancelled: CancellationException) {
            created.forEach(::deleteDocumentQuietly)
            throw cancelled
        } catch (error: Exception) {
            created.forEach(::deleteDocumentQuietly)
            throw ReportExportException(
                ReportExportError.SAVE_FAILED,
                "保存失败",
                error
            )
        }
    }

    suspend fun savePdfToUri(
        file: File,
        destination: Uri,
        fallbackDisplayName: String
    ): SavedExportLocation =
        withContext(Dispatchers.IO) {
            var committed = false
            try {
                appContext.contentResolver.openOutputStream(destination, "w")
                    ?.use { output ->
                        file.inputStream().buffered().use { input ->
                            input.copyToCancellable(output)
                        }
                    } ?: error("无法打开 PDF 保存位置")
                committed = true
                SavedExportLocation(
                    displayName = runCatching {
                        queryDisplayName(destination)
                    }.getOrNull() ?: fallbackDisplayName,
                    destination = ExportDestination.USER_SELECTED,
                    displayLocation = null
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                throw ReportExportException(
                    ReportExportError.SAVE_FAILED,
                    "保存失败",
                    error
                )
            } finally {
                if (!committed) deleteUriQuietly(destination)
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

    fun deleteTemporary(file: File?) {
        file?.takeIf { it.parentFile == temporaryDirectory }?.delete()
    }

    private fun ensureTemporaryDirectory() {
        check(temporaryDirectory.mkdirs() || temporaryDirectory.isDirectory)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun findBatchSequence(
        collection: Uri,
        relativePath: String,
        names: List<String>
    ): Int {
        val resolver = appContext.contentResolver
        for (sequence in 1 until 10_000) {
            val collision = names.any { requested ->
                val candidate = ExportFileNames.withSequence(requested, sequence)
                resolver.query(
                    collection,
                    arrayOf(MediaStore.MediaColumns._ID),
                    "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND " +
                        "${MediaStore.MediaColumns.DISPLAY_NAME}=?",
                    arrayOf(relativePath, candidate),
                    null
                )?.use { it.moveToFirst() } == true
            }
            if (!collision) return sequence
        }
        throw ReportExportException(ReportExportError.SAVE_FAILED, "保存失败")
    }

    private fun queryTreeNames(treeUri: Uri, documentId: String): Set<String> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            documentId
        )
        return appContext.contentResolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val index = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            )
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(index))
            }
        }.orEmpty()
    }

    private fun firstAvailableSequence(
        existingNames: Set<String>,
        requestedNames: List<String>
    ): Int {
        for (sequence in 1 until 10_000) {
            if (requestedNames.none {
                    ExportFileNames.withSequence(it, sequence) in existingNames
                }
            ) {
                return sequence
            }
        }
        throw ReportExportException(ReportExportError.SAVE_FAILED, "保存失败")
    }

    private fun deleteUriQuietly(uri: Uri) {
        try {
            appContext.contentResolver.delete(uri, null, null)
        } catch (error: Exception) {
            Log.w(TAG, "Unable to delete incomplete report output", error)
        }
    }

    private fun deleteDocumentQuietly(uri: Uri) {
        try {
            DocumentsContract.deleteDocument(appContext.contentResolver, uri)
        } catch (error: Exception) {
            Log.w(TAG, "Unable to delete incomplete SAF report page", error)
        }
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
        const val TAG = "ReportFileStore"
    }
}
