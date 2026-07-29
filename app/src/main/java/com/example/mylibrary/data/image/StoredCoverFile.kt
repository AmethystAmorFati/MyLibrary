package com.example.mylibrary.data.image

import android.content.Context
import java.io.File

fun resolveStoredCoverFile(context: Context, relativePath: String?): File? {
    val path = relativePath?.takeIf { it.isNotBlank() } ?: return null
    if (File(path).isAbsolute) return null
    return runCatching {
        val root = context.filesDir.canonicalFile
        val target = File(root, path).canonicalFile
        target.takeIf {
            it.path.startsWith(root.path + File.separator) && it.isFile
        }
    }.getOrNull()
}
