package com.example.mylibrary.ui.theme.importer

import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

internal object ThemePackageFileOps {
    fun requireChild(parent: File, child: File): File {
        val canonicalParent = parent.canonicalFile
        val canonicalChild = child.canonicalFile
        if (
            canonicalChild == canonicalParent ||
            !canonicalChild.toPath().startsWith(canonicalParent.toPath())
        ) {
            failThemePackage(
                ThemePackageError.ZipPathEscapesRoot(child.path)
            )
        }
        return canonicalChild
    }

    fun deleteTree(target: File, allowedParent: File): Boolean {
        if (!target.exists() && !Files.isSymbolicLink(target.toPath())) return true
        val checked = try {
            requireChild(allowedParent, target)
        } catch (_: Exception) {
            return false
        }
        return try {
            Files.walkFileTree(
                checked.toPath(),
                object : SimpleFileVisitor<Path>() {
                    override fun visitFile(
                        file: Path,
                        attrs: BasicFileAttributes
                    ): FileVisitResult {
                        Files.deleteIfExists(file)
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFileFailed(
                        file: Path,
                        exc: IOException
                    ): FileVisitResult {
                        if (Files.isSymbolicLink(file)) {
                            Files.deleteIfExists(file)
                            return FileVisitResult.CONTINUE
                        }
                        throw exc
                    }

                    override fun postVisitDirectory(
                        dir: Path,
                        exc: IOException?
                    ): FileVisitResult {
                        if (exc != null) throw exc
                        Files.deleteIfExists(dir)
                        return FileVisitResult.CONTINUE
                    }
                }
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    fun clearDirectory(directory: File): Boolean {
        if (!directory.exists()) return directory.mkdirs()
        if (!directory.isDirectory || Files.isSymbolicLink(directory.toPath())) {
            return false
        }
        return directory.listFiles().orEmpty().all { child ->
            deleteTree(child, directory)
        }
    }
}
