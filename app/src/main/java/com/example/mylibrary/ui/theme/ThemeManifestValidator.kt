package com.example.mylibrary.ui.theme

data class ThemeValidationIssue(
    val field: String,
    val message: String
)

data class ThemeValidationResult(
    val issues: List<ThemeValidationIssue>
) {
    val isValid: Boolean
        get() = issues.isEmpty()
}

object ThemeManifestValidator {
    private val themeIdPattern = Regex("^[a-z0-9][a-z0-9._-]*$")
    private val argbColorPattern = Regex("^#[0-9A-Fa-f]{8}$")

    fun validate(manifest: ThemeManifest): ThemeValidationResult {
        val issues = mutableListOf<ThemeValidationIssue>()

        if (manifest.schemaVersion != THEME_MANIFEST_SCHEMA_VERSION) {
            issues += ThemeValidationIssue(
                "schemaVersion",
                "Only schema version $THEME_MANIFEST_SCHEMA_VERSION is supported"
            )
        }
        validateRequiredString("id", manifest.id, ThemeResourceLimits.MAX_THEME_ID_LENGTH, issues)
        if (manifest.id.isNotEmpty() && !themeIdPattern.matches(manifest.id)) {
            issues += ThemeValidationIssue(
                "id",
                "Use lowercase ASCII letters, digits, dot, underscore, or hyphen"
            )
        }
        validateRequiredString(
            "name",
            manifest.name,
            ThemeResourceLimits.MAX_MANIFEST_STRING_LENGTH,
            issues
        )
        validateRequiredString(
            "version",
            manifest.version,
            ThemeResourceLimits.MAX_MANIFEST_STRING_LENGTH,
            issues
        )
        manifest.author?.let {
            validateRequiredString(
                "author",
                it,
                ThemeResourceLimits.MAX_MANIFEST_STRING_LENGTH,
                issues
            )
        }

        manifest.surfaces.entries().forEach { (role, definition) ->
            validateSurface(role, definition, issues)
        }
        val surfaceImageCount = manifest.surfaces.entries()
            .count { it.second.type == ThemeSurfaceType.IMAGE }
        if (surfaceImageCount > ThemeResourceLimits.MAX_SURFACE_IMAGES) {
            issues += ThemeValidationIssue(
                "surfaces",
                "At most ${ThemeResourceLimits.MAX_SURFACE_IMAGES} surface images are allowed"
            )
        }

        manifest.colors.entries().forEach { (name, value) ->
            validateColor("colors.$name", value, issues)
        }

        validateFontPath("fonts.fontA", manifest.fonts.fontA, issues)
        validateFontPath("fonts.fontB", manifest.fonts.fontB, issues)
        val fontFileCount = listOf(manifest.fonts.fontA, manifest.fonts.fontB)
            .count { it != null }
        if (fontFileCount > ThemeResourceLimits.MAX_FONT_FILES) {
            issues += ThemeValidationIssue(
                "fonts",
                "At most ${ThemeResourceLimits.MAX_FONT_FILES} font files are allowed"
            )
        }

        FontRole.entries.forEach { role ->
            if (role !in manifest.fontAssignments) {
                issues += ThemeValidationIssue(
                    "fontAssignments.${role.name}",
                    "Every font role must be assigned to slot A or B"
                )
            }
        }

        validateNavigation(manifest.navigationIcons, issues)
        return ThemeValidationResult(issues)
    }

    fun isArgbColor(value: String): Boolean = argbColorPattern.matches(value)

    private fun validateSurface(
        role: SurfaceRole,
        definition: ThemeSurfaceDefinition,
        issues: MutableList<ThemeValidationIssue>
    ) {
        val field = "surfaces.${role.name.lowercase()}"
        validateColor("$field.color", definition.color, issues)
        when (definition.type) {
            ThemeSurfaceType.COLOR -> {
                if (definition.file != null) {
                    issues += ThemeValidationIssue(
                        "$field.file",
                        "COLOR surfaces must not declare a file"
                    )
                }
            }

            ThemeSurfaceType.IMAGE -> {
                val file = definition.file
                if (file.isNullOrBlank()) {
                    issues += ThemeValidationIssue(
                        "$field.file",
                        "IMAGE surfaces require a resource file"
                    )
                    return
                }
                val extensions = when (role) {
                    SurfaceRole.BACKGROUND -> {
                        ThemeResourceLimits.BACKGROUND_IMAGE_EXTENSIONS
                    }

                    SurfaceRole.CARD -> {
                        ThemeResourceLimits.COMMON_IMAGE_EXTENSIONS
                    }

                    SurfaceRole.DIALOG -> {
                        ThemeResourceLimits.COMMON_IMAGE_EXTENSIONS
                    }
                }
                validateResourcePath(
                    "$field.file",
                    file,
                    ThemeResourceLimits.SURFACE_PREFIX,
                    extensions,
                    issues
                )
                if (!ThemeResourceLimits.isAllowedSurfaceImagePath(role, file)) {
                    issues += ThemeValidationIssue(
                        "$field.file",
                        "Surface image path must match the $role role"
                    )
                }
            }
        }
    }

    private fun validateColor(
        field: String,
        value: String,
        issues: MutableList<ThemeValidationIssue>
    ) {
        if (!isArgbColor(value)) {
            issues += ThemeValidationIssue(field, "Color must use #AARRGGBB")
        }
    }

    private fun validateFontPath(
        field: String,
        value: String?,
        issues: MutableList<ThemeValidationIssue>
    ) {
        if (value == null) return
        if (value.isBlank()) {
            issues += ThemeValidationIssue(field, "Font path must not be blank")
            return
        }
        validateResourcePath(
            field,
            value,
            ThemeResourceLimits.FONT_PREFIX,
            ThemeResourceLimits.FONT_EXTENSIONS,
            issues
        )
    }

    private fun validateNavigation(
        navigation: ThemeNavigationManifest?,
        issues: MutableList<ThemeValidationIssue>
    ) {
        if (navigation == null) return
        val entries = navigation.entries()
        if (entries.isEmpty()) {
            issues += ThemeValidationIssue(
                "navigationIcons",
                "Navigation configuration must contain at least one icon slot"
            )
        }
        var imageCount = 0
        entries.forEach { (slot, definition) ->
            val field = "navigationIcons.${slot.name.lowercase()}"
            if (definition.normal.isBlank()) {
                issues += ThemeValidationIssue(
                    "$field.normal",
                    "Configured slots require a normal icon"
                )
            } else {
                imageCount += 1
                validateNavigationPath("$field.normal", definition.normal, issues)
            }
            definition.selected?.let { selected ->
                if (selected.isBlank()) {
                    issues += ThemeValidationIssue(
                        "$field.selected",
                        "Selected icon path must not be blank"
                    )
                } else {
                    imageCount += 1
                    validateNavigationPath("$field.selected", selected, issues)
                }
            }
        }
        if (imageCount > ThemeResourceLimits.MAX_NAVIGATION_IMAGES) {
            issues += ThemeValidationIssue(
                "navigationIcons",
                "At most ${ThemeResourceLimits.MAX_NAVIGATION_IMAGES} navigation images are allowed"
            )
        }
    }

    private fun validateNavigationPath(
        field: String,
        value: String,
        issues: MutableList<ThemeValidationIssue>
    ) {
        validateResourcePath(
            field,
            value,
            ThemeResourceLimits.NAVIGATION_PREFIX,
            ThemeResourceLimits.COMMON_IMAGE_EXTENSIONS,
            issues
        )
    }

    private fun validateResourcePath(
        field: String,
        path: String,
        requiredPrefix: String,
        allowedExtensions: Set<String>,
        issues: MutableList<ThemeValidationIssue>
    ) {
        if (path.length > ThemeResourceLimits.MAX_MANIFEST_STRING_LENGTH) {
            issues += ThemeValidationIssue(
                field,
                "Resource path exceeds ${ThemeResourceLimits.MAX_MANIFEST_STRING_LENGTH} characters"
            )
        }
        if (
            path.startsWith("/") ||
            Regex("^[A-Za-z]:").containsMatchIn(path) ||
            '\\' in path
        ) {
            issues += ThemeValidationIssue(
                field,
                "Resource path must be relative and use forward slashes"
            )
        }
        if (".." in path || path.split('/').any { it == "." || it.isEmpty() }) {
            issues += ThemeValidationIssue(field, "Path traversal is not allowed")
        }
        if (!path.startsWith(requiredPrefix)) {
            issues += ThemeValidationIssue(
                field,
                "Resource must be stored under $requiredPrefix"
            )
        }
        val fileName = path.substringAfterLast('/')
        if (fileName.length > ThemeResourceLimits.MAX_FILE_NAME_LENGTH) {
            issues += ThemeValidationIssue(
                field,
                "File name exceeds ${ThemeResourceLimits.MAX_FILE_NAME_LENGTH} characters"
            )
        }
        val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase()
        if (extension !in allowedExtensions) {
            issues += ThemeValidationIssue(
                field,
                "Allowed extensions: ${allowedExtensions.sorted().joinToString()}"
            )
        }
    }

    private fun validateRequiredString(
        field: String,
        value: String,
        maxLength: Int,
        issues: MutableList<ThemeValidationIssue>
    ) {
        if (value.isBlank()) {
            issues += ThemeValidationIssue(field, "Value must not be blank")
        }
        if (value.length > maxLength) {
            issues += ThemeValidationIssue(field, "Value exceeds $maxLength characters")
        }
    }
}
