package com.example.mylibrary.backup

import com.example.mylibrary.data.preferences.ThemePreferenceStore
import com.example.mylibrary.ui.theme.DefaultThemeManifest
import com.example.mylibrary.ui.theme.ThemeApplyResult
import com.example.mylibrary.ui.theme.ThemeRepository
import com.example.mylibrary.ui.theme.importer.InstalledThemeCatalog
import com.example.mylibrary.ui.theme.importer.InstalledThemeStatus
import com.example.mylibrary.ui.theme.importer.ThemeInstaller
import com.example.mylibrary.ui.theme.importer.ThemePackageResult
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Collects installed themes for inclusion in a backup ZIP.
 * Only themes that pass the installed-directory validation are included.
 */
data class ThemeExportEntry(
    val themeId: String,
    val files: Map<String, File>
)

data class ThemeExportCollection(
    val themes: List<ThemeExportEntry>,
    val skippedCount: Int,
    val currentThemeId: String?
)

data class ThemeRestoreResult(
    val installedThemeIds: Set<String>,
    val skippedCount: Int,
    val currentThemeRestored: Boolean
)

/**
 * Bridges the backup system and the theme installation system without
 * duplicating validation rules.  Export reads from the installed theme
 * directory using [InstalledThemeCatalog]; import restores themes via
 * [ThemeInstaller] with the same atomic-replacement mechanism used by
 * interactive theme import.
 */
class BackupThemeTransfer(
    private val installedDirectory: File,
    private val themeInstaller: ThemeInstaller,
    private val installedThemeCatalog: InstalledThemeCatalog,
    private val themePreferenceStore: ThemePreferenceStore? = null,
    private val themeRepository: ThemeRepository? = null,
    private val logger: BackupLogger = AndroidBackupLogger(TAG),
    private val generationSource: () -> Long = System::nanoTime
) {

    suspend fun collectThemesForExport(): ThemeExportCollection =
        withContext(Dispatchers.IO) {
            val metadata = installedThemeCatalog.listInstalledThemes()
            val validIds = metadata
                .filter { it.status == InstalledThemeStatus.VALID }
                .map { it.id }
                .toSet()

            val entries = mutableListOf<ThemeExportEntry>()
            var skippedCount = metadata.count {
                it.status == InstalledThemeStatus.INVALID
            }

            for (themeId in validIds) {
                coroutineContext.ensureActive()
                val directory = File(installedDirectory, themeId)
                if (!directory.isDirectory || Files.isSymbolicLink(directory.toPath())) {
                    skippedCount += 1
                    continue
                }
                val files = try {
                    collectRegularFiles(directory)
                } catch (error: Exception) {
                    logger.warning("Skipping theme $themeId during export: ${error.message}")
                    skippedCount += 1
                    continue
                }
                if (files.isEmpty()) {
                    skippedCount += 1
                    continue
                }
                entries += ThemeExportEntry(themeId, files)
            }

            val currentThemeId = readCurrentThemeIdForExport(validIds)
            ThemeExportCollection(
                themes = entries,
                skippedCount = skippedCount,
                currentThemeId = currentThemeId
            )
        }

    private suspend fun readCurrentThemeIdForExport(
        validIds: Set<String>
    ): String? {
        val store = themePreferenceStore ?: return null
        val persistedId = try {
            store.readCurrentThemeId()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            logger.warning("Unable to read current theme ID for export: ${error.message}")
            return null
        }
        if (persistedId == null || persistedId == DefaultThemeManifest.id) {
            return null
        }
        // If the current theme was skipped (corrupted), write null.
        return persistedId.takeIf { it in validIds }
    }

    /**
     * Restores themes from a validated backup extraction.
     *
     * Each theme directory from the backup is copied into a staging
     * directory and then atomically installed via [ThemeInstaller.install],
     * which re-validates the manifest, checksums and resource limits.
     *
     * Individual theme failures do not abort the overall restore.
     */
    suspend fun restoreThemes(
        themeDirectories: Map<String, File>,
        currentThemeId: String?
    ): ThemeRestoreResult = withContext(Dispatchers.IO) {
        val installedIds = mutableSetOf<String>()
        var skippedCount = 0

        for ((themeId, sourceDirectory) in themeDirectories) {
            coroutineContext.ensureActive()
            val result = restoreSingleTheme(themeId, sourceDirectory)
            if (result != null) {
                installedIds += themeId
            } else {
                skippedCount += 1
            }
        }

        val currentRestored = restoreCurrentTheme(
            currentThemeId,
            installedIds
        )
        ThemeRestoreResult(
            installedThemeIds = installedIds,
            skippedCount = skippedCount,
            currentThemeRestored = currentRestored
        )
    }

    private suspend fun restoreSingleTheme(
        themeId: String,
        sourceDirectory: File
    ): Boolean {
        if (!sourceDirectory.isDirectory || Files.isSymbolicLink(sourceDirectory.toPath())) {
            logger.warning("Theme $themeId: source directory is unavailable")
            return false
        }
        val staging = when (val prepared = themeInstaller.prepareStagingDirectory()) {
            is ThemePackageResult.Success -> prepared.value
            is ThemePackageResult.Failure -> {
                logger.warning("Theme $themeId: staging preparation failed: ${prepared.error}")
                return false
            }
        }
        return try {
            copyThemeFiles(sourceDirectory, staging)
            when (
                val installed = themeInstaller.install(
                    staging = staging,
                    themeId = themeId,
                    themeGeneration = generationSource()
                )
            ) {
                is ThemePackageResult.Success -> {
                    logger.info("Theme $themeId restored successfully")
                    true
                }
                is ThemePackageResult.Failure -> {
                    logger.warning("Theme $themeId: installation failed: ${installed.error}")
                    cleanupStaging(staging)
                    false
                }
            }
        } catch (cancelled: CancellationException) {
            cleanupStaging(staging)
            throw cancelled
        } catch (error: Exception) {
            logger.warning("Theme $themeId: unexpected failure: ${error.message}", error)
            cleanupStaging(staging)
            false
        }
    }

    private fun copyThemeFiles(source: File, staging: File) {
        val canonicalStaging = staging.canonicalFile
        Files.walkFileTree(
            source.canonicalFile.toPath(),
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(
                    file: Path,
                    attrs: BasicFileAttributes
                ): FileVisitResult {
                    if (!attrs.isRegularFile || Files.isSymbolicLink(file)) {
                        return FileVisitResult.CONTINUE
                    }
                    val relative = source.canonicalFile.toPath()
                        .relativize(file)
                        .joinToString("/") { it.toString() }
                    val destination = File(canonicalStaging, relative).canonicalFile
                    require(
                        destination.toPath().startsWith(canonicalStaging.toPath())
                    ) { "Theme file escapes staging directory" }
                    destination.parentFile?.mkdirs()
                    file.toFile().copyTo(destination, overwrite = true)
                    return FileVisitResult.CONTINUE
                }
            }
        )
    }

    private fun cleanupStaging(staging: File) {
        try {
            themeInstaller.stagingDirectory.listFiles()?.forEach { child ->
                if (child.canonicalFile == staging.canonicalFile) {
                    child.deleteRecursively()
                }
            }
        } catch (error: Exception) {
            logger.warning("Staging cleanup failed: ${error.message}")
        }
    }

    private suspend fun restoreCurrentTheme(
        currentThemeId: String?,
        installedIds: Set<String>
    ): Boolean {
        val store = themePreferenceStore
        val repository = themeRepository
        if (store == null || repository == null) return true

        if (currentThemeId == null) {
            applyDefaultThemeSafely(repository)
            return true
        }
        if (currentThemeId !in installedIds) {
            logger.warning(
                "Current theme $currentThemeId was not restored; " +
                    "falling back to default theme"
            )
            applyDefaultThemeSafely(repository)
            return false
        }
        return when (val result = repository.applyInstalledTheme(currentThemeId)) {
            is ThemeApplyResult.Applied -> true
            is ThemeApplyResult.AlreadyCurrent -> true
            is ThemeApplyResult.Failure -> {
                logger.warning(
                    "Current theme $currentThemeId could not be applied; " +
                        "falling back to default theme"
                )
                applyDefaultThemeSafely(repository)
                false
            }
        }
    }

    private suspend fun applyDefaultThemeSafely(repository: ThemeRepository) {
        try {
            repository.applyDefaultTheme()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            logger.warning("Default theme application failed: ${error.message}")
        }
    }

    /**
     * Emergency fallback used when [restoreThemes] fails with an unexpected
     * exception.  For v5 backups the theme preference must not be left
     * pointing at a potentially-corrupted theme, so we clear it and apply
     * the default theme.  This method never throws.
     */
    suspend fun fallbackToDefaultTheme() {
        val repository = themeRepository
        if (repository != null) {
            applyDefaultThemeSafely(repository)
        }
        try {
            themePreferenceStore?.clearCurrentThemeId()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            logger.warning("Failed to clear theme preference during fallback: ${error.message}")
        }
    }

    private fun collectRegularFiles(directory: File): Map<String, File> {
        val files = linkedMapOf<String, File>()
        val canonicalRoot = directory.canonicalFile
        Files.walkFileTree(
            canonicalRoot.toPath(),
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(
                    file: Path,
                    attrs: BasicFileAttributes
                ): FileVisitResult {
                    if (attrs.isRegularFile && !Files.isSymbolicLink(file)) {
                        val relative = canonicalRoot.toPath()
                            .relativize(file)
                            .joinToString("/") { it.toString() }
                        files[relative] = file.toFile()
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(
                    file: Path,
                    exc: java.io.IOException
                ): FileVisitResult {
                    if (Files.isSymbolicLink(file)) {
                        return FileVisitResult.CONTINUE
                    }
                    return FileVisitResult.TERMINATE
                }
            }
        )
        return files
    }

    companion object {
        private const val TAG = "BackupThemeTransfer"
    }
}
