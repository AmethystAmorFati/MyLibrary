package com.example.mylibrary.ui.theme

import androidx.compose.ui.graphics.Color
import com.example.mylibrary.ui.navigation.DefaultNavigationIconResolver
import com.example.mylibrary.ui.navigation.NavigationIconResolver
import com.example.mylibrary.ui.navigation.ResolvedNavigationIconResolver

internal data class ThemeFallbackResolution(
    val theme: ResolvedTheme,
    val failure: ThemeResolveError?
)

object ThemeResolver {
    /**
     * Performs blocking validation, sampled image decoding, and font loading.
     * Callers must run this away from the main thread; Repository restoration
     * already uses Dispatchers.IO.
     */
    fun resolveStrict(
        manifest: ThemeManifest,
        resources: ThemeResourceProvider,
        themeGeneration: Long = 0L,
        imageDecodeProfile: ThemeImageDecodeProfile? = null
    ): ThemeResolveResult =
        try {
            resolveStrictInternal(
                manifest = manifest,
                resources = resources,
                themeGeneration = themeGeneration,
                imageDecodeProfile = imageDecodeProfile
            )
        } catch (exception: ThemeResourceAccessException) {
            ThemeResolveResult.Failure(exception.error)
        } catch (exception: Exception) {
            ThemeResolveResult.Failure(
                ThemeResolveError.UnexpectedFailure(
                    exception.message ?: exception::class.java.simpleName
                )
            )
        }

    private fun resolveStrictInternal(
        manifest: ThemeManifest,
        resources: ThemeResourceProvider,
        themeGeneration: Long,
        imageDecodeProfile: ThemeImageDecodeProfile?
    ): ThemeResolveResult {
        val validation = ThemeManifestValidator.validate(manifest)
        if (!validation.isValid) {
            return ThemeResolveResult.Failure(
                ThemeResolveError.ManifestInvalid(validation.issues)
            )
        }

        val imageAssets = if (
            manifest.surfaces.entries()
                .any { it.second.type == ThemeSurfaceType.IMAGE }
        ) {
            val loader = ThemeImageLoader(
                resources = resources,
                themeId = manifest.id,
                themeVersion = manifest.version,
                themeGeneration = themeGeneration,
                decodeProfile = imageDecodeProfile
            )
            when (val result = loader.load(manifest.surfaces)) {
                is ThemeImageLoadResult.Success -> result.images
                is ThemeImageLoadResult.Failure -> {
                    return ThemeResolveResult.Failure(result.error)
                }
            }
        } else {
            emptyMap()
        }

        val navigationIconResolver: NavigationIconResolver =
            manifest.navigationIcons?.let { navigation ->
                when (
                    val result = ThemeNavigationIconLoader(
                        resources = resources,
                        themeId = manifest.id,
                        themeVersion = manifest.version,
                        themeGeneration = themeGeneration
                    ).load(navigation)
                ) {
                    is ThemeNavigationIconLoadResult.Success ->
                        ResolvedNavigationIconResolver(result.resources)

                    is ThemeNavigationIconLoadResult.Failure ->
                        return ThemeResolveResult.Failure(result.error)
                }
            } ?: DefaultNavigationIconResolver

        val fontLoadResult = ThemeFontLoader(
            resources = resources,
            themeId = manifest.id,
            themeVersion = manifest.version,
            themeGeneration = themeGeneration
        ).load(manifest.fonts)
        if (fontLoadResult is ThemeFontLoadResult.Failure) {
            return ThemeResolveResult.Failure(fontLoadResult.error)
        }
        val loadedFonts = fontLoadResult as ThemeFontLoadResult.Success
        val fontResolver: AppFontResolver =
            if (loadedFonts.fontA == null && loadedFonts.fontB == null) {
                SystemAppFontResolver
            } else {
                ThemeFontResolver(
                    fontA = loadedFonts.fontA,
                    fontB = loadedFonts.fontB,
                    assignments = manifest.fontAssignments
                )
            }

        val surfaces = ResolvedThemeSurfaces(
            background = resolveSurface(
                role = SurfaceRole.BACKGROUND,
                definition = manifest.surfaces.background,
                imageAssets = imageAssets
            ),
            card = resolveSurface(
                role = SurfaceRole.CARD,
                definition = manifest.surfaces.card,
                imageAssets = imageAssets
            ),
            dialog = resolveSurface(
                role = SurfaceRole.DIALOG,
                definition = manifest.surfaces.dialog,
                imageAssets = imageAssets
            )
        )
        val colors = AppContentColors(
            textPrimary = parseThemeColor(manifest.colors.textPrimary),
            textSecondary = parseThemeColor(manifest.colors.textSecondary),
            border = parseThemeColor(manifest.colors.border),
            accent = parseThemeColor(manifest.colors.accent),
            onAccent = parseThemeColor(manifest.colors.onAccent)
        )
        return ThemeResolveResult.Success(
            ResolvedTheme(
                id = manifest.id,
                name = manifest.name,
                surfaces = surfaces,
                colors = colors,
                typography = createAppTypography(fontResolver),
                fontResolver = fontResolver,
                navigationIconResolver = navigationIconResolver,
                darkSystemBarIcons = manifest.darkSystemBarIcons
            )
        )
    }

    /**
     * Startup recovery only. UI and import code must use [resolveStrict].
     */
    internal fun resolveOrDefault(
        manifest: ThemeManifest,
        resources: ThemeResourceProvider,
        themeGeneration: Long = 0L,
        imageDecodeProfile: ThemeImageDecodeProfile? = null
    ): ThemeFallbackResolution =
        when (
            val result = resolveStrict(
                manifest = manifest,
                resources = resources,
                themeGeneration = themeGeneration,
                imageDecodeProfile = imageDecodeProfile
            )
        ) {
            is ThemeResolveResult.Success -> {
                ThemeFallbackResolution(theme = result.theme, failure = null)
            }

            is ThemeResolveResult.Failure -> {
                ThemeFallbackResolution(
                    theme = DefaultResolvedTheme,
                    failure = result.error
                )
            }
        }

    internal fun parseThemeColor(value: String): Color =
        Color(value.drop(1).toLong(radix = 16))

    private fun resolveSurface(
        role: SurfaceRole,
        definition: ThemeSurfaceDefinition,
        imageAssets: Map<SurfaceRole, ThemeImageAsset>
    ): ResolvedSurface {
        val fallbackColor = parseThemeColor(definition.color)
        return when (definition.type) {
            ThemeSurfaceType.COLOR ->
                ResolvedSurface.ColorSurface(fallbackColor)

            ThemeSurfaceType.IMAGE ->
                ResolvedSurface.ImageSurface(
                    fallbackColor = fallbackColor,
                    image = imageAssets.getValue(role),
                    role = role
                )
        }
    }
}
