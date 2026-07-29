package com.example.mylibrary.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.mylibrary.ui.navigation.NavigationIconResolver

private val LocalResolvedTheme = staticCompositionLocalOf { DefaultResolvedTheme }

object AppTheme {
    val resolvedTheme: ResolvedTheme
        @Composable
        @ReadOnlyComposable
        get() = LocalResolvedTheme.current

    val surfaces: ResolvedThemeSurfaces
        @Composable
        @ReadOnlyComposable
        get() = LocalResolvedTheme.current.surfaces

    val contentColors: AppContentColors
        @Composable
        @ReadOnlyComposable
        get() = LocalResolvedTheme.current.colors

    val colors: AppColors
        @Composable
        @ReadOnlyComposable
        get() = LocalResolvedTheme.current.appColors

    val typography: AppTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalResolvedTheme.current.typography

    val fontResolver: AppFontResolver
        @Composable
        @ReadOnlyComposable
        get() = LocalResolvedTheme.current.fontResolver

    val navigationIconResolver: NavigationIconResolver
        @Composable
        @ReadOnlyComposable
        get() = LocalResolvedTheme.current.navigationIconResolver

    val darkSystemBarIcons: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalResolvedTheme.current.darkSystemBarIcons

    @Composable
    @ReadOnlyComposable
    fun surface(role: SurfaceRole): ResolvedSurface =
        LocalResolvedTheme.current.surfaces[role]
}

internal fun AppColors.toMaterialScheme(): ColorScheme = lightColorScheme(
    primary = accent,
    onPrimary = onAccent,
    primaryContainer = subtleCard,
    onPrimaryContainer = textPrimary,
    inversePrimary = onAccent,
    secondary = textSecondary,
    onSecondary = onAccent,
    secondaryContainer = subtleCard,
    onSecondaryContainer = textPrimary,
    tertiary = accent,
    onTertiary = onAccent,
    tertiaryContainer = subtleCard,
    onTertiaryContainer = textPrimary,
    background = surfaces.background,
    onBackground = textPrimary,
    surface = surfaces.card,
    onSurface = textPrimary,
    surfaceVariant = subtleCard,
    onSurfaceVariant = textSecondary,
    surfaceTint = androidx.compose.ui.graphics.Color.Transparent,
    inverseSurface = textPrimary,
    inverseOnSurface = surfaces.card,
    error = AppError,
    onError = AppOnError,
    errorContainer = AppErrorContainer,
    onErrorContainer = AppOnErrorContainer,
    outline = border,
    outlineVariant = subtleBorder,
    scrim = AppScrim,
    surfaceBright = surfaces.card,
    surfaceDim = subtleCard,
    surfaceContainer = surfaces.card,
    surfaceContainerHigh = subtleCard,
    surfaceContainerHighest = subtleCard,
    surfaceContainerLow = surfaces.card,
    surfaceContainerLowest = surfaces.card
)

@Composable
fun MyLibraryTheme(
    resolvedTheme: ResolvedTheme = DefaultResolvedTheme,
    content: @Composable () -> Unit
) {
    val colors = resolvedTheme.appColors

    CompositionLocalProvider(
        LocalResolvedTheme provides resolvedTheme
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialScheme(),
            typography = resolvedTheme.typography.toMaterialTypography(),
            shapes = LibraryShapes,
            content = content
        )
    }
}
