package com.example.mylibrary.ui.theme.importer

import com.example.mylibrary.ui.theme.ThemeManifest
import com.example.mylibrary.ui.theme.ThemeManifestValidator
import com.example.mylibrary.ui.theme.ThemeResourceLimits
import com.example.mylibrary.ui.theme.ThemeSurfaceType
import com.example.mylibrary.ui.theme.ThemeValidationIssue
import com.example.mylibrary.ui.theme.ThemeValidationResult
import java.util.Locale

/**
 * Output policy for future package producers.
 *
 * Import and installed-theme loading continue accepting the Phase 3 role
 * subdirectories. New packages must use the three canonical root file names.
 */
object ThemePackageAuthoringPolicy {
    fun validateCanonicalOutput(
        manifest: ThemeManifest
    ): ThemeValidationResult {
        val issues = ThemeManifestValidator.validate(manifest)
            .issues
            .toMutableList()
        manifest.surfaces.entries().forEach { (role, definition) ->
            if (
                definition.type == ThemeSurfaceType.IMAGE &&
                definition.file?.let {
                    ThemeResourceLimits.isCanonicalSurfaceImagePath(role, it)
                } != true
            ) {
                issues += ThemeValidationIssue(
                    field = "surfaces.${role.name.lowercase(Locale.ROOT)}.file",
                    message = "New packages must use the canonical surface path"
                )
            }
        }
        return ThemeValidationResult(issues)
    }
}
