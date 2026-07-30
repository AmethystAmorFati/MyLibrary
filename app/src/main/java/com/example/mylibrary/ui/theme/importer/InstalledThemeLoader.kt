package com.example.mylibrary.ui.theme.importer

import com.example.mylibrary.ui.theme.DirectoryThemeResourceProvider
import com.example.mylibrary.ui.theme.ThemeResolver
import com.example.mylibrary.ui.theme.ThemeResolveResult
import java.io.File

fun interface InstalledThemeDirectoryLoader {
    suspend fun load(
        installedDirectory: File,
        themeGeneration: Long
    ): ThemePackageResult<InstalledTheme>
}

class InstalledThemeLoader internal constructor(
    private val directoryValidator: InstalledThemeDirectoryValidator
) : InstalledThemeDirectoryLoader {
    constructor() : this(InstalledThemeDirectoryValidator())

    override suspend fun load(
        installedDirectory: File,
        themeGeneration: Long
    ): ThemePackageResult<InstalledTheme> {
        val validated = when (
            val result = directoryValidator.validate(installedDirectory)
        ) {
            is ThemePackageResult.Success -> result.value
            is ThemePackageResult.Failure -> return result
        }

        val resolved = when (
            val result = ThemeResolver.resolveStrict(
                manifest = validated.manifest,
                resources = DirectoryThemeResourceProvider(
                    validated.directory
                ),
                themeGeneration = themeGeneration
            )
        ) {
            is ThemeResolveResult.Success -> result.theme
            is ThemeResolveResult.Failure -> {
                return ThemePackageResult.Failure(
                    ThemePackageError.ThemeResolutionFailed(result.error)
                )
            }
        }
        return ThemePackageResult.Success(
            InstalledTheme(
                id = validated.manifest.id,
                name = validated.manifest.name,
                version = validated.manifest.version,
                author = validated.manifest.author,
                directory = validated.directory,
                resolvedTheme = resolved
            )
        )
    }
}
