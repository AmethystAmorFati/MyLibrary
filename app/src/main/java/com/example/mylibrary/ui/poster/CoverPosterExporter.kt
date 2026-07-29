package com.example.mylibrary.ui.poster

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.mylibrary.domain.model.LibraryItem
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CoverPosterExporter {
    suspend fun createShareUri(
        context: Context,
        items: List<LibraryItem>,
        palette: CoverPosterPalette
    ): Uri = withContext(Dispatchers.IO) {
        val poster = CoverPosterRenderer.render(context, items, palette)
        var outputFile: File? = null
        try {
            val directory = File(context.cacheDir, "shared/posters")
            val file = writePosterFileAtomically(directory) { staging ->
                FileOutputStream(staging).use { output ->
                    check(
                        poster.compress(
                            android.graphics.Bitmap.CompressFormat.JPEG,
                            94,
                            output
                        )
                    ) {
                        "海报保存失败"
                    }
                }
            }
            outputFile = file
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (error: Throwable) {
            outputFile?.delete()
            throw error
        } finally {
            poster.recycle()
        }
    }

    fun shareIntent(context: Context, uri: Uri): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, "封面海报", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "分享封面海报")
    }
}

internal fun writePosterFileAtomically(
    directory: File,
    writeStagingFile: (File) -> Unit
): File {
    check(directory.mkdirs() || directory.isDirectory) {
        "无法创建海报分享目录"
    }
    val staging = File.createTempFile("cover-poster-", ".tmp", directory)
    val output = File(
        directory,
        staging.name.removeSuffix(".tmp") + ".jpg"
    )
    try {
        writeStagingFile(staging)
        check(staging.isFile && staging.length() > 0L) {
            "海报编码未生成有效文件"
        }
        check(staging.renameTo(output)) {
            "海报临时文件提交失败"
        }
        return output
    } catch (error: Throwable) {
        staging.delete()
        output.delete()
        throw error
    }
}
