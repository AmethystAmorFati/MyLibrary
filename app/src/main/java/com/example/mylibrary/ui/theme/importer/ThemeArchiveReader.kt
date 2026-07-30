package com.example.mylibrary.ui.theme.importer

import java.io.File
import java.io.FileOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipException
import java.util.zip.ZipFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

data class ExtractedThemeArchive(
    val rootDirectory: File,
    val files: Map<String, File>
)

class ThemeArchiveReader {
    suspend fun extract(
        archive: File,
        stagingDirectory: File
    ): ThemePackageResult<ExtractedThemeArchive> {
        val metadata = when (val result = ThemeArchiveMetadataReader.read(archive)) {
            is ThemePackageResult.Success -> result.value
            is ThemePackageResult.Failure -> return result
        }
        return try {
            if (!stagingDirectory.exists() && !stagingDirectory.mkdirs()) {
                failThemePackage(
                    ThemePackageError.InstallFailed(
                        "Unable to create the staging directory"
                    )
                )
            }
            if (!ThemePackageFileOps.clearDirectory(stagingDirectory)) {
                failThemePackage(
                    ThemePackageError.InstallFailed(
                        "Unable to prepare an empty staging directory"
                    )
                )
            }
            val canonicalRoot = stagingDirectory.canonicalFile
            val files = linkedMapOf<String, File>()
            var totalBytes = 0L

            ZipFile(archive).use { zip ->
                val zipEntries = zip.entries().asSequence().toList()
                if (zipEntries.size != metadata.entries.size) {
                    invalidZip("ZIP entry count changed while opening the archive")
                }
                metadata.entries.forEach { declared ->
                    coroutineContext.ensureActive()
                    val entry = zipEntries.singleOrNull {
                        it.name == declared.path
                    } ?: invalidZip(
                        "Central entry cannot be reopened: ${declared.path}"
                    )
                    if (
                        entry.size != declared.uncompressedSize ||
                        entry.compressedSize != declared.compressedSize ||
                        entry.crc != declared.crc32 ||
                        entry.method != declared.compressionMethod
                    ) {
                        invalidZip(
                            "ZIP metadata changed while reopening " +
                                declared.path
                        )
                    }
                    if (entry.isDirectory !=
                        (declared.kind == ThemeArchiveEntryKind.DIRECTORY)
                    ) {
                        invalidZip("ZIP entry type is inconsistent")
                    }
                    if (declared.kind == ThemeArchiveEntryKind.DIRECTORY) {
                        val directory = File(
                            canonicalRoot,
                            declared.path.removeSuffix("/")
                        ).canonicalFile
                        ensureInside(canonicalRoot, directory, declared.path)
                        if (!directory.mkdirs() && !directory.isDirectory) {
                            failThemePackage(
                                ThemePackageError.InstallFailed(
                                    "Unable to create ${declared.path}"
                                )
                            )
                        }
                        return@forEach
                    }

                    val destination = File(
                        canonicalRoot,
                        declared.path
                    ).canonicalFile
                    ensureInside(canonicalRoot, destination, declared.path)
                    destination.parentFile?.let { parent ->
                        if (!parent.mkdirs() && !parent.isDirectory) {
                            failThemePackage(
                                ThemePackageError.InstallFailed(
                                    "Unable to create a staging subdirectory"
                                )
                            )
                        }
                    }
                    val crc = CRC32()
                    var entryBytes = 0L
                    zip.getInputStream(entry).buffered().use { input ->
                        FileOutputStream(destination).buffered().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                coroutineContext.ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                if (count == 0) continue
                                entryBytes += count
                                totalBytes += count
                                if (entryBytes > declared.uncompressedSize) {
                                    sizeMismatch(
                                        declared,
                                        entryBytes
                                    )
                                }
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
                                crc.update(buffer, 0, count)
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    if (entryBytes != declared.uncompressedSize) {
                        sizeMismatch(declared, entryBytes)
                    }
                    if (crc.value != declared.crc32) {
                        failThemePackage(
                            ThemePackageError.ArchiveEntryCrcMismatch(
                                path = declared.path,
                                expectedCrc32 = declared.crc32,
                                actualCrc32 = crc.value
                            )
                        )
                    }
                    files[declared.path] = destination
                }
            }
            if (ThemePackageLimits.MANIFEST_PATH !in files) {
                failThemePackage(ThemePackageError.MissingManifest)
            }
            if (ThemePackageLimits.CHECKSUMS_PATH !in files) {
                failThemePackage(ThemePackageError.MissingChecksums)
            }
            ThemePackageResult.Success(
                ExtractedThemeArchive(
                    rootDirectory = canonicalRoot,
                    files = files.toMap()
                )
            )
        } catch (cancelled: CancellationException) {
            ThemePackageFileOps.clearDirectory(stagingDirectory)
            throw cancelled
        } catch (failure: ThemePackageFailureException) {
            ThemePackageFileOps.clearDirectory(stagingDirectory)
            ThemePackageResult.Failure(failure.error)
        } catch (exception: ZipException) {
            ThemePackageFileOps.clearDirectory(stagingDirectory)
            ThemePackageResult.Failure(
                ThemePackageError.NotZipArchive(
                    exception.message ?: "ZIP CRC or structure is invalid"
                )
            )
        } catch (exception: Exception) {
            ThemePackageFileOps.clearDirectory(stagingDirectory)
            ThemePackageResult.Failure(
                ThemePackageError.NotZipArchive(
                    exception.message ?: exception::class.java.simpleName
                )
            )
        }
    }

    private fun ensureInside(
        root: File,
        destination: File,
        path: String
    ) {
        if (
            destination == root ||
            !destination.toPath().startsWith(root.toPath())
        ) {
            failThemePackage(ThemePackageError.ZipPathEscapesRoot(path))
        }
    }

    private fun sizeMismatch(
        declared: ThemeArchiveEntryMetadata,
        actualBytes: Long
    ): Nothing = failThemePackage(
        ThemePackageError.ArchiveEntrySizeMismatch(
            path = declared.path,
            declaredBytes = declared.uncompressedSize,
            actualBytes = actualBytes
        )
    )

    private fun invalidZip(reason: String): Nothing =
        failThemePackage(ThemePackageError.NotZipArchive(reason))
}
