package com.example.mylibrary.ui.theme

import androidx.compose.ui.graphics.Color
import com.example.mylibrary.ui.navigation.DefaultNavigationIconResolver

val DefaultThemeManifest = ThemeManifest(
    schemaVersion = THEME_MANIFEST_SCHEMA_VERSION,
    id = "builtin.default",
    name = "MyLibrary 默认主题",
    author = "MyLibrary",
    version = "1.0.0",
    surfaces = ThemeSurfaceManifest(
        background = ThemeSurfaceDefinition(
            type = ThemeSurfaceType.COLOR,
            color = "#FFF3F3F1",
            file = null
        ),
        card = ThemeSurfaceDefinition(
            type = ThemeSurfaceType.COLOR,
            color = "#FFFFFFFF",
            file = null
        ),
        dialog = ThemeSurfaceDefinition(
            type = ThemeSurfaceType.COLOR,
            color = "#FFFFFFFF",
            file = null
        )
    ),
    colors = ThemeColorManifest(
        textPrimary = "#FF111111",
        textSecondary = "#FF555555",
        border = "#FFD2D2D2",
        accent = "#FF111111",
        onAccent = "#FFFFFFFF"
    ),
    fonts = ThemeFontManifest(
        fontA = null,
        fontB = null
    ),
    fontAssignments = FontRole.entries.associateWith { it.defaultSlot },
    navigationIcons = null,
    darkSystemBarIcons = true
)

val DefaultResolvedTheme = ResolvedTheme(
    id = "builtin.default",
    name = "MyLibrary 默认主题",
    surfaces = ResolvedThemeSurfaces(
        background = ResolvedSurface.ColorSurface(Color(0xFFF3F3F1)),
        card = ResolvedSurface.ColorSurface(Color(0xFFFFFFFF)),
        dialog = ResolvedSurface.ColorSurface(Color(0xFFFFFFFF))
    ),
    colors = AppContentColors(
        textPrimary = Color(0xFF111111),
        textSecondary = Color(0xFF555555),
        border = Color(0xFFD2D2D2),
        accent = Color(0xFF111111),
        onAccent = Color(0xFFFFFFFF)
    ),
    typography = createAppTypography(SystemAppFontResolver),
    fontResolver = SystemAppFontResolver,
    navigationIconResolver = DefaultNavigationIconResolver,
    darkSystemBarIcons = true,
    themeGeneration = 0L
)
