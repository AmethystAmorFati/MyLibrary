package com.example.mylibrary.ui.theme.importer

import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.Locale

internal object ThemePackageDirectoryScanner {
    fun scan(rootDirectory: File): ThemePackageResult<Map<String, File>> =
        try {
            val root = rootDirectory.canonicalFile
            if (
                !root.isDirectory ||
                Files.isSymbolicLink(root.toPath())
            ) {
                failThemePackage(
                    ThemePackageError.InstallFailed(
                        "Installed theme root is not a regular directory"
                    )
                )
            }
            val files = linkedMapOf<String, File>()
            val casePaths = mutableMapOf<String, String>()
            var totalEntries = 0
            var totalBytes = 0L
            Files.walkFileTree(
                root.toPath(),
                object : SimpleFileVisitor<Path>() {
                    override fun preVisitDirectory(
                        dir: Path,
                        attrs: BasicFileAttributes
                    ): FileVisitResult {
                        if (dir != root.toPath()) {
                            totalEntries += 1
                            checkEntryCount(totalEntries)
                            val path = relative(root, dir.toFile()) + "/"
                            if (Files.isSymbolicLink(dir)) {
                                failThemePackage(
                                    ThemePackageError.UnsupportedEntryType(
                                        path,
                                        "symbolic link"
                                    )
                                )
                            }
                            ThemeArchivePathPolicy.validate(
                                path,
                                ThemeArchiveEntryKind.DIRECTORY
                            )
                            checkCase(casePaths, path)
                        }
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFile(
                        file: Path,
                        attrs: BasicFileAttributes
                    ): FileVisitResult {
                        totalEntries += 1
                        checkEntryCount(totalEntries)
                        val path = relative(root, file.toFile())
                        if (
                            Files.isSymbolicLink(file) ||
                            !attrs.isRegularFile
                        ) {
                            failThemePackage(
                                ThemePackageError.UnsupportedEntryType(
                                    path,
                                    "non-regular file"
                                )
                            )
                        }
                        ThemeArchivePathPolicy.validate(
                            path,
                            ThemeArchiveEntryKind.FILE
                        )
                        checkCase(casePaths, path)
                        if (files.size + 1 > ThemePackageLimits.MAX_FILE_ENTRIES) {
                            failThemePackage(
                                ThemePackageError.TooManyFiles(
                                    files.size + 1,
                                    ThemePackageLimits.MAX_FILE_ENTRIES
                                )
                            )
                        }
                        totalBytes = Math.addExact(totalBytes, attrs.size())
                        if (
                            totalBytes >
                            ThemePackageLimits.MAX_TOTAL_UNCOMPRESSED_BYTES
                        ) {
                            failThemePackage(
                                ThemePackageError
                                    .ArchiveUncompressedSizeExceeded(
                                        totalBytes,
                                        ThemePackageLimits
                                            .MAX_TOTAL_UNCOMPRESSED_BYTES
                                    )
                            )
                        }
                        files[path] = file.toFile()
                        return FileVisitResult.CONTINUE
                    }
                }
            )
            if (ThemePackageLimits.MANIFEST_PATH !in files) {
                failThemePackage(ThemePackageError.MissingManifest)
            }
            if (ThemePackageLimits.CHECKSUMS_PATH !in files) {
                failThemePackage(ThemePackageError.MissingChecksums)
            }
            ThemePackageResult.Success(files)
        } catch (failure: ThemePackageFailureException) {
            ThemePackageResult.Failure(failure.error)
        } catch (exception: Exception) {
            ThemePackageResult.Failure(
                ThemePackageError.InstallFailed(
                    exception.message ?: exception::class.java.simpleName
                )
            )
        }

    private fun relative(root: File, file: File): String =
        root.toPath()
            .relativize(file.canonicalFile.toPath())
            .joinToString("/") { it.toString() }

    private fun checkEntryCount(actual: Int) {
        if (actual > ThemePackageLimits.MAX_TOTAL_ENTRIES) {
            failThemePackage(
                ThemePackageError.TooManyEntries(
                    actual,
                    ThemePackageLimits.MAX_TOTAL_ENTRIES
                )
            )
        }
    }

    private fun checkCase(
        seen: MutableMap<String, String>,
        path: String
    ) {
        val prior = seen.putIfAbsent(path.lowercase(Locale.ROOT), path)
        if (prior != null && prior != path) {
            failThemePackage(ThemePackageError.CaseCollision(prior, path))
        }
    }
}
