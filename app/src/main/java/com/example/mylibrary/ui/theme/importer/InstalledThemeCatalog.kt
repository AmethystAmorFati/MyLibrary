package com.example.mylibrary.ui.theme.importer

import com.example.mylibrary.ui.theme.DefaultThemeManifest
import com.example.mylibrary.ui.theme.ThemeResourceLimits
import java.io.File
import java.nio.file.Files
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class InstalledThemeStatus {
    VALID,
    INVALID
}

data class InstalledThemeMetadata(
    val id: String,
    val name: String?,
    val author: String?,
    val version: String?,
    val status: InstalledThemeStatus,
    val error: ThemePackageError? = null
)

sealed interface ThemeDeleteError {
    data class InvalidThemeId(val themeId: String) : ThemeDeleteError
    data object DefaultThemeProtected : ThemeDeleteError
    data class DeleteFailed(val themeId: String) : ThemeDeleteError
}

sealed interface ThemeDeleteResult {
    data object Success : ThemeDeleteResult
    data class Failure(val error: ThemeDeleteError) : ThemeDeleteResult
}

interface InstalledThemeCatalog {
    suspend fun listInstalledThemes(
        currentThemeId: String? = null
    ): List<InstalledThemeMetadata>

    suspend fun load(
        themeId: String,
        themeGeneration: Long
    ): ThemePackageResult<InstalledTheme>

    suspend fun delete(themeId: String): ThemeDeleteResult
}

class FileInstalledThemeCatalog(
    rootDirectory: File,
    private val installedThemeLoader: InstalledThemeDirectoryLoader =
        InstalledThemeLoader(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : InstalledThemeCatalog {
    private val installedRoot = rootDirectory.absoluteFile
    private val directoryValidator = InstalledThemeDirectoryValidator()

    override suspend fun listInstalledThemes(
        currentThemeId: String?
    ): List<InstalledThemeMetadata> = withContext(ioDispatcher) {
        if (!ensureInstalledRoot()) return@withContext emptyList()
        val items = mutableListOf<InstalledThemeMetadata>()
        val directories = installedRoot.listFiles()
            .orEmpty()
            .filter(::isCatalogDirectory)
        for (directory in directories) {
            items += readMetadata(directory)
        }
        items.sortedWith(
            compareBy<InstalledThemeMetadata>(
                { if (it.id == currentThemeId) 0 else 1 },
                {
                    (it.name ?: INVALID_SORT_NAME)
                        .lowercase(Locale.ROOT)
                },
                { it.id }
            )
        )
    }

    override suspend fun load(
        themeId: String,
        themeGeneration: Long
    ): ThemePackageResult<InstalledTheme> = withContext(ioDispatcher) {
        if (!isValidThemeId(themeId) || themeId == DefaultThemeManifest.id) {
            return@withContext ThemePackageResult.Failure(
                ThemePackageError.InstallFailed("Invalid installed theme ID")
            )
        }
        if (!ensureInstalledRoot()) {
            return@withContext ThemePackageResult.Failure(
                ThemePackageError.InstallFailed(
                    "Installed theme storage is unavailable"
                )
            )
        }
        val directory = try {
            ThemePackageFileOps.requireChild(
                installedRoot,
                File(installedRoot, themeId)
            )
        } catch (failure: ThemePackageFailureException) {
            return@withContext ThemePackageResult.Failure(failure.error)
        }
        if (
            !directory.isDirectory ||
            Files.isSymbolicLink(directory.toPath())
        ) {
            return@withContext ThemePackageResult.Failure(
                ThemePackageError.InstallFailed(
                    "Installed theme directory is unavailable"
                )
            )
        }
        when (
            val loaded = installedThemeLoader.load(
                directory,
                themeGeneration
            )
        ) {
            is ThemePackageResult.Success -> {
                if (loaded.value.id == themeId) {
                    loaded
                } else {
                    ThemePackageResult.Failure(
                        ThemePackageError.ManifestParseFailed(
                            "Manifest ID does not match the installed directory"
                        )
                    )
                }
            }

            is ThemePackageResult.Failure -> loaded
        }
    }

    override suspend fun delete(
        themeId: String
    ): ThemeDeleteResult = withContext(ioDispatcher) {
        if (themeId == DefaultThemeManifest.id) {
            return@withContext ThemeDeleteResult.Failure(
                ThemeDeleteError.DefaultThemeProtected
            )
        }
        if (!isValidThemeId(themeId)) {
            return@withContext ThemeDeleteResult.Failure(
                ThemeDeleteError.InvalidThemeId(themeId)
            )
        }
        if (!ensureInstalledRoot()) {
            return@withContext ThemeDeleteResult.Failure(
                ThemeDeleteError.DeleteFailed(themeId)
            )
        }
        val target = try {
            ThemePackageFileOps.requireChild(
                installedRoot,
                File(installedRoot, themeId)
            )
        } catch (_: ThemePackageFailureException) {
            return@withContext ThemeDeleteResult.Failure(
                ThemeDeleteError.InvalidThemeId(themeId)
            )
        }
        if (!target.exists() && !Files.isSymbolicLink(target.toPath())) {
            return@withContext ThemeDeleteResult.Success
        }
        if (ThemePackageFileOps.deleteTree(target, installedRoot)) {
            ThemeDeleteResult.Success
        } else {
            ThemeDeleteResult.Failure(
                ThemeDeleteError.DeleteFailed(themeId)
            )
        }
    }

    private suspend fun readMetadata(
        directory: File
    ): InstalledThemeMetadata {
        val result = directoryValidator.validate(directory)
        return when (result) {
            is ThemePackageResult.Success -> {
                val manifest = result.value.manifest
                if (manifest.id != directory.name) {
                    invalidMetadata(
                        id = directory.name,
                        error = ThemePackageError.ManifestParseFailed(
                            "Manifest ID does not match the installed directory"
                        )
                    )
                } else {
                    InstalledThemeMetadata(
                        id = manifest.id,
                        name = manifest.name,
                        author = manifest.author,
                        version = manifest.version,
                        status = InstalledThemeStatus.VALID
                    )
                }
            }

            is ThemePackageResult.Failure ->
                invalidMetadata(directory.name, result.error)
        }
    }

    private fun invalidMetadata(
        id: String,
        error: ThemePackageError
    ): InstalledThemeMetadata = InstalledThemeMetadata(
        id = id,
        name = null,
        author = null,
        version = null,
        status = InstalledThemeStatus.INVALID,
        error = error
    )

    private fun isCatalogDirectory(file: File): Boolean =
        isValidThemeId(file.name) &&
            file.isDirectory &&
            !Files.isSymbolicLink(file.toPath())

    private fun ensureInstalledRoot(): Boolean =
        (
            installedRoot.isDirectory ||
                (!installedRoot.exists() && installedRoot.mkdirs())
            ) &&
            !Files.isSymbolicLink(installedRoot.toPath())

    private fun isValidThemeId(themeId: String): Boolean =
        themeId.length <= ThemeResourceLimits.MAX_THEME_ID_LENGTH &&
            THEME_ID_PATTERN.matches(themeId)

    private companion object {
        val THEME_ID_PATTERN = Regex("^[a-z0-9][a-z0-9._-]*$")
        const val INVALID_SORT_NAME = "\uFFFF"
    }
}
