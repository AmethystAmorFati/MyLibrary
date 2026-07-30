package com.example.mylibrary.ui.theme.importer

import android.content.Context
import android.util.Log
import com.example.mylibrary.ui.theme.DirectoryThemeResourceProvider
import com.example.mylibrary.ui.theme.ThemeManifest
import com.example.mylibrary.ui.theme.ThemeResolver
import com.example.mylibrary.ui.theme.ThemeResolveResult
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface ThemePackageImportService {
    suspend fun import(
        source: ThemePackageSource
    ): ThemePackageImportResult

    suspend fun peekManifest(
        source: ThemePackageSource
    ): ThemePackageResult<ThemeManifest>
}

class ThemePackageImporter(
    private val temporaryRoot: File,
    private val installer: ThemeInstaller,
    private val archiveReader: ThemeArchiveReader = ThemeArchiveReader(),
    private val codec: ThemePackageJsonCodec = ThemePackageJsonCodec(),
    private val checksumValidator: ThemeChecksumValidator =
        ThemeChecksumValidator(),
    private val consistencyValidator: ThemePackageConsistencyValidator =
        ThemePackageConsistencyValidator(),
    private val generationSource: () -> Long = System::nanoTime
) : ThemePackageImportService {
    override suspend fun import(
        source: ThemePackageSource
    ): ThemePackageImportResult = withContext(Dispatchers.IO) {
        processImportMutex.withLock {
            importLocked(source)
        }
    }

    override suspend fun peekManifest(
        source: ThemePackageSource
    ): ThemePackageResult<ThemeManifest> = withContext(Dispatchers.IO) {
        peekManifestLocked(source)
    }

    private suspend fun peekManifestLocked(
        source: ThemePackageSource
    ): ThemePackageResult<ThemeManifest> {
        if (!temporaryRoot.exists() && !temporaryRoot.mkdirs()) {
            return ThemePackageResult.Failure(
                ThemePackageError.SourceReadFailed(
                    "Unable to create a private import directory"
                )
            )
        }
        if (!temporaryRoot.isDirectory) {
            return ThemePackageResult.Failure(
                ThemePackageError.SourceReadFailed(
                    "Private import path is not a directory"
                )
            )
        }

        val workDirectory = File(
            temporaryRoot,
            "theme-peek-${UUID.randomUUID()}"
        )
        val archive = File(workDirectory, "source.mylibrarytheme")
        var manifestText: String? = null
        return try {
            if (!workDirectory.mkdirs()) {
                failThemePackage(
                    ThemePackageError.SourceReadFailed(
                        "Unable to create a private import workspace"
                    )
                )
            }
            when (val copied = source.copyTo(archive)) {
                is ThemePackageCopyResult.Success -> Unit
                is ThemePackageCopyResult.Failure ->
                    return ThemePackageResult.Failure(copied.error)
            }
            FileInputStream(archive).use { fileInput ->
                ZipInputStream(fileInput).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (entry.name == ThemePackageLimits.MANIFEST_PATH) {
                            val buffer = ByteArrayOutputStream()
                            val data = ByteArray(DEFAULT_BUFFER_SIZE)
                            var total = 0L
                            while (true) {
                                val count = zip.read(data)
                                if (count < 0) break
                                total += count
                                if (
                                    total >
                                    ThemePackageLimits.MAX_MANIFEST_BYTES
                                ) {
                                    failThemePackage(
                                        ThemePackageError.ManifestTooLarge(
                                            total,
                                            ThemePackageLimits.MAX_MANIFEST_BYTES
                                        )
                                    )
                                }
                                buffer.write(data, 0, count)
                            }
                            manifestText =
                                buffer.toString(Charsets.UTF_8.name())
                            break
                        }
                        entry = zip.nextEntry
                    }
                }
            }
            val text = manifestText
                ?: return ThemePackageResult.Failure(
                    ThemePackageError.MissingManifest
                )
            codec.decodeManifest(text)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: ThemePackageFailureException) {
            ThemePackageResult.Failure(failure.error)
        } catch (exception: Exception) {
            ThemePackageResult.Failure(
                ThemePackageError.InstallFailed(
                    exception.message ?: exception::class.java.simpleName
                )
            )
        } finally {
            if (archive.exists()) archive.delete()
            if (workDirectory.exists()) {
                ThemePackageFileOps.deleteTree(
                    workDirectory,
                    temporaryRoot
                )
            }
        }
    }

    private suspend fun importLocked(
        source: ThemePackageSource
    ): ThemePackageImportResult {
        var phase = ThemeImportPhase.RECOVER_INTERRUPTED

        fun fail(error: ThemePackageError): ThemePackageImportResult {
            Log.e(TAG, "phase=$phase error=$error")
            return ThemePackageImportResult.Failure(error)
        }

        when (val recovery = installer.recoverInterruptedOperations()) {
            is ThemePackageResult.Success -> Unit
            is ThemePackageResult.Failure ->
                return fail(recovery.error)
        }
        phase = ThemeImportPhase.PREPARE_WORKSPACE
        if (!temporaryRoot.exists() && !temporaryRoot.mkdirs()) {
            return fail(
                ThemePackageError.SourceReadFailed(
                    "Unable to create a private import directory"
                )
            )
        }
        if (!temporaryRoot.isDirectory) {
            return fail(
                ThemePackageError.SourceReadFailed(
                    "Private import path is not a directory"
                )
            )
        }

        val workDirectory = File(
            temporaryRoot,
            "theme-import-${UUID.randomUUID()}"
        )
        val archive = File(workDirectory, "source.mylibrarytheme")
        var staging: File? = null
        return try {
            if (!workDirectory.mkdirs()) {
                failThemePackage(
                    ThemePackageError.SourceReadFailed(
                        "Unable to create a private import workspace"
                    )
                )
            }
            phase = ThemeImportPhase.COPY_SOURCE
            when (val copied = source.copyTo(archive)) {
                is ThemePackageCopyResult.Success -> Unit
                is ThemePackageCopyResult.Failure ->
                    return fail(copied.error)
            }
            phase = ThemeImportPhase.PREPARE_STAGING
            val stagingDirectory = when (
                val prepared = installer.prepareStagingDirectory()
            ) {
                is ThemePackageResult.Success -> prepared.value
                is ThemePackageResult.Failure ->
                    return fail(prepared.error)
            }
            staging = stagingDirectory
            phase = ThemeImportPhase.EXTRACT_ARCHIVE
            val extracted = when (
                val result = archiveReader.extract(archive, stagingDirectory)
            ) {
                is ThemePackageResult.Success -> result.value
                is ThemePackageResult.Failure ->
                    return fail(result.error)
            }

            phase = ThemeImportPhase.READ_CHECKSUMS
            val checksumsFile = extracted.files[ThemePackageLimits.CHECKSUMS_PATH]
            if (checksumsFile == null) {
                return fail(ThemePackageError.MissingChecksums)
            }
            val checksumsText = when (
                val read = checksumsFile.readUtf8TextWithLimit(
                    limit = ThemePackageLimits.MAX_CHECKSUMS_BYTES,
                    tooLarge = { actual, maximum ->
                        ThemePackageError.ChecksumsTooLarge(actual, maximum)
                    },
                    invalidText = {
                        ThemePackageError.ChecksumsInvalid(it)
                    }
                )
            ) {
                is ThemePackageResult.Success -> read.value
                is ThemePackageResult.Failure ->
                    return fail(read.error)
            }
            phase = ThemeImportPhase.PARSE_CHECKSUMS
            val checksums = when (
                val decoded = codec.decodeChecksums(checksumsText)
            ) {
                is ThemePackageResult.Success -> decoded.value
                is ThemePackageResult.Failure ->
                    return fail(decoded.error)
            }
            phase = ThemeImportPhase.VERIFY_CHECKSUMS
            when (
                val validated = checksumValidator.validate(
                    extracted.files,
                    checksums
                )
            ) {
                is ThemePackageResult.Success -> Unit
                is ThemePackageResult.Failure ->
                    return fail(validated.error)
            }

            phase = ThemeImportPhase.READ_MANIFEST
            val manifestFile = extracted.files[ThemePackageLimits.MANIFEST_PATH]
            if (manifestFile == null) {
                return fail(ThemePackageError.MissingManifest)
            }
            val manifestText = when (
                val read = manifestFile.readUtf8TextWithLimit(
                    limit = ThemePackageLimits.MAX_MANIFEST_BYTES,
                    tooLarge = { actual, maximum ->
                        ThemePackageError.ManifestTooLarge(actual, maximum)
                    },
                    invalidText = {
                        ThemePackageError.ManifestParseFailed(it)
                    }
                )
            ) {
                is ThemePackageResult.Success -> read.value
                is ThemePackageResult.Failure ->
                    return fail(read.error)
            }
            phase = ThemeImportPhase.PARSE_MANIFEST
            val manifest = when (
                val decoded = codec.decodeManifest(manifestText)
            ) {
                is ThemePackageResult.Success -> decoded.value
                is ThemePackageResult.Failure ->
                    return fail(decoded.error)
            }
            phase = ThemeImportPhase.VALIDATE_MANIFEST
            when (
                val validation = consistencyValidator.validate(
                    manifest,
                    extracted.files
                )
            ) {
                is ThemePackageResult.Success -> Unit
                is ThemePackageResult.Failure ->
                    return fail(validation.error)
            }

            phase = ThemeImportPhase.RESOLVE_THEME
            val generation = generationSource()
            when (
                val strict = ThemeResolver.resolveStrict(
                    manifest = manifest,
                    resources = DirectoryThemeResourceProvider(
                        extracted.rootDirectory
                    ),
                    themeGeneration = generation
                )
            ) {
                is ThemeResolveResult.Success -> Unit
                is ThemeResolveResult.Failure ->
                    return fail(
                        ThemePackageError.ThemeResolutionFailed(strict.error)
                    )
            }

            phase = ThemeImportPhase.INSTALL_THEME
            when (
                val installed = installer.install(
                    staging = stagingDirectory,
                    themeId = manifest.id,
                    themeGeneration = generation
                )
            ) {
                is ThemePackageResult.Success -> {
                    staging = null
                    ThemePackageImportResult.Installed(installed.value)
                }

                is ThemePackageResult.Failure ->
                    fail(installed.error)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: ThemePackageFailureException) {
            Log.e(TAG, "phase=$phase error=${failure.error}", failure)
            ThemePackageImportResult.Failure(failure.error)
        } catch (exception: Exception) {
            Log.e(
                TAG,
                "phase=$phase unexpected ${exception::class.java.simpleName}: " +
                    "${exception.message}",
                exception
            )
            ThemePackageImportResult.Failure(
                ThemePackageError.InstallFailed(
                    exception.message ?: exception::class.java.simpleName
                )
            )
        } finally {
            if (archive.exists()) archive.delete()
            if (workDirectory.exists()) {
                ThemePackageFileOps.deleteTree(
                    workDirectory,
                    temporaryRoot
                )
            }
            staging?.takeIf { it.exists() }?.let {
                ThemePackageFileOps.deleteTree(
                    it,
                    installer.stagingDirectory
                )
            }
        }
    }

    companion object {
        private const val TAG = "ThemePackageImporter"
        private val processImportMutex = Mutex()

        fun create(context: Context): ThemePackageImporter {
            val appContext = context.applicationContext
            return ThemePackageImporter(
                temporaryRoot = File(
                    appContext.cacheDir,
                    "theme-package-imports"
                ),
                installer = ThemeInstaller(
                    File(appContext.filesDir, "themes")
                )
            )
        }
    }
}
