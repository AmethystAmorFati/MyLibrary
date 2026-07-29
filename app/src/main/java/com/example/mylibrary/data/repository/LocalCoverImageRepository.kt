package com.example.mylibrary.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.example.mylibrary.data.image.CoverImageProcessor
import com.example.mylibrary.data.image.CoverInputValidator
import com.example.mylibrary.data.image.resolveStoredCoverFile
import com.example.mylibrary.domain.model.CoverStorageLimits
import com.example.mylibrary.domain.model.StoredCoverImage
import com.example.mylibrary.domain.repository.CoverImageRepository
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class LocalCoverImageRepository(
    context: Context
) : CoverImageRepository {
    private val appContext = context.applicationContext
    private val originalDirectory = File(appContext.filesDir, "images/original")
    private val thumbnailDirectory = File(appContext.filesDir, "images/thumbnail")

    override suspend fun save(uri: String): StoredCoverImage = withContext(Dispatchers.IO) {
        val sourceUri = Uri.parse(uri)
        val mimeType = appContext.contentResolver.getType(sourceUri).orEmpty()
        require(mimeType.isEmpty() || mimeType.startsWith("image/")) {
            "请选择有效图片"
        }
        val stagingDirectory = File(appContext.cacheDir, "cover-staging").apply {
            check(mkdirs() || isDirectory) { "无法创建封面临时目录" }
        }
        val staged = File.createTempFile("cover-source-", ".tmp", stagingDirectory)
        try {
            val input = requireNotNull(
                appContext.contentResolver.openInputStream(sourceUri)
            ) {
                "无法读取所选图片"
            }
            input.buffered().use { source ->
                FileOutputStream(staged).buffered().use { output ->
                    copyCoverWithLimit(source, output)
                }
            }
            val metadata = CoverInputValidator.validate(
                file = staged,
                declaredMimeType = mimeType
            )
            storeValidatedSource(staged, metadata.format)
        } finally {
            if (!staged.delete() && staged.exists()) {
                Log.w(LOG_TAG, "Cover staging file could not be deleted: ${staged.name}")
            }
        }
    }

    override fun resolveOriginal(relativePath: String?): File? =
        resolveStoredCoverFile(appContext, relativePath)

    override suspend fun importOriginal(source: File): StoredCoverImage =
        withContext(Dispatchers.IO) {
            val metadata = CoverInputValidator.validate(
                file = source,
                declaredExtension = source.extension
            )
            storeValidatedSource(source, metadata.format)
        }

    override suspend fun delete(
        originalPath: String?,
        thumbnailPath: String?
    ) = withContext(Dispatchers.IO) {
        listOfNotNull(originalPath, thumbnailPath)
            .mapNotNull { resolveStoredCoverFile(appContext, it) }
            .forEach { file ->
                check(file.delete() || !file.exists()) { "Cover deletion failed" }
            }
    }

    private suspend fun storeValidatedSource(
        source: File,
        extension: String
    ): StoredCoverImage {
        check(originalDirectory.mkdirs() || originalDirectory.isDirectory) {
            "无法创建封面目录"
        }
        check(thumbnailDirectory.mkdirs() || thumbnailDirectory.isDirectory) {
            "无法创建缩略图目录"
        }
        val id = UUID.randomUUID().toString()
        val original = File(originalDirectory, "$id.$extension")
        val thumbnail = File(thumbnailDirectory, "$id.jpg")
        var bitmap: Bitmap? = null
        try {
            bitmap = CoverImageProcessor.decodeThumbnail(
                openStream = { source.inputStream() }
            )
            source.inputStream().buffered().use { input ->
                FileOutputStream(original).buffered().use { output ->
                    copyCoverWithLimit(input, output)
                }
            }
            FileOutputStream(thumbnail).use { output ->
                check(
                    requireNotNull(bitmap).compress(
                        Bitmap.CompressFormat.JPEG,
                        86,
                        output
                    )
                ) {
                    "缩略图保存失败"
                }
            }
            return StoredCoverImage(
                originalPath = original.relativePath(),
                thumbnailPath = thumbnail.relativePath()
            )
        } catch (error: Throwable) {
            if (!original.delete() && original.exists()) {
                Log.w(LOG_TAG, "Partial original cover could not be deleted: ${original.name}")
            }
            if (!thumbnail.delete() && thumbnail.exists()) {
                Log.w(LOG_TAG, "Partial thumbnail could not be deleted: ${thumbnail.name}")
            }
            throw error
        } finally {
            bitmap?.recycle()
        }
    }

    private suspend fun copyCoverWithLimit(
        input: InputStream,
        output: OutputStream
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            coroutineContext.ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            total += count
            require(total <= CoverStorageLimits.MAX_SOURCE_BYTES) {
                "封面文件不能超过 32 MiB"
            }
            output.write(buffer, 0, count)
        }
    }

    private fun File.relativePath(): String =
        relativeTo(appContext.filesDir).invariantSeparatorsPath

    private companion object {
        const val LOG_TAG = "MyLibraryCover"
    }
}
