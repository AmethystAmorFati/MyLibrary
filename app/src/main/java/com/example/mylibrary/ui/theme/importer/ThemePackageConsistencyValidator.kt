package com.example.mylibrary.ui.theme.importer

import com.example.mylibrary.ui.theme.ThemeManifest
import com.example.mylibrary.ui.theme.ThemeManifestValidator
import com.example.mylibrary.ui.theme.ThemeSurfaceType
import java.io.File

class ThemePackageConsistencyValidator {
    fun validate(
        manifest: ThemeManifest,
        files: Map<String, File>
    ): ThemePackageResult<Unit> =
        try {
            val validation = ThemeManifestValidator.validate(manifest)
            if (!validation.isValid) {
                failThemePackage(
                    ThemePackageError.ThemeValidationFailed(validation.issues)
                )
            }
            val referenced = linkedSetOf<String>()
            manifest.surfaces.entries().forEach { (_, definition) ->
                if (definition.type == ThemeSurfaceType.IMAGE) {
                    referenced += requireNotNull(definition.file)
                }
            }
            manifest.fonts.fontA?.let(referenced::add)
            manifest.fonts.fontB?.let(referenced::add)
            manifest.navigationIcons?.entries()?.forEach { (_, definition) ->
                referenced += definition.normal
                definition.selected?.let(referenced::add)
            }

            val actualResources = files.keys - setOf(
                ThemePackageLimits.MANIFEST_PATH,
                ThemePackageLimits.CHECKSUMS_PATH
            )
            (referenced - actualResources).sorted().firstOrNull()?.let {
                failThemePackage(ThemePackageError.ManifestResourceMissing(it))
            }
            (actualResources - referenced).sorted().firstOrNull()?.let {
                failThemePackage(ThemePackageError.ManifestResourceExtra(it))
            }
            ThemePackageResult.Success(Unit)
        } catch (failure: ThemePackageFailureException) {
            ThemePackageResult.Failure(failure.error)
        } catch (exception: Exception) {
            ThemePackageResult.Failure(
                ThemePackageError.ManifestParseFailed(
                    exception.message ?: exception::class.java.simpleName
                )
            )
        }
}
