package com.example.mylibrary.ui.theme

import android.graphics.Typeface
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.font.FontFamily

@Immutable
sealed interface FontSource {
    data object System : FontSource

    data class ThemeFile(
        val cacheKey: ThemeFontCacheKey
    ) : FontSource
}

@Immutable
data class ThemeFontCacheKey(
    val themeId: String,
    val themeVersion: String,
    val themeGeneration: Long,
    val slot: FontSlot,
    val relativePath: String,
    val fileSize: Long,
    val lastModified: Long
)

@Immutable
data class ResolvedFontSlot(
    val composeFontFamily: FontFamily,
    val androidTypeface: Typeface,
    val source: FontSource.ThemeFile
)

/**
 * One immutable mapping supplies both Compose and Canvas outputs.
 *
 * A null resolved slot represents the system font. Declared-but-invalid files
 * never reach this class because strict resolution fails before construction.
 */
@Immutable
class ThemeFontResolver(
    private val fontA: ResolvedFontSlot?,
    private val fontB: ResolvedFontSlot?,
    assignments: Map<FontRole, FontSlot>
) : AppFontResolver {
    private val roleAssignments = assignments.toMap()

    init {
        require(FontRole.entries.all { it in roleAssignments }) {
            "Every font role must be assigned"
        }
    }

    override fun composeFontFamily(role: FontRole): FontFamily =
        resolvedSlot(role)?.composeFontFamily ?: FontFamily.Default

    override fun androidTypeface(role: FontRole): Typeface =
        resolvedSlot(role)?.androidTypeface ?: Typeface.DEFAULT

    fun source(role: FontRole): FontSource =
        resolvedSlot(role)?.source ?: FontSource.System

    internal fun resolvedSlot(role: FontRole): ResolvedFontSlot? {
        val effectiveSlot = ThemeFontFallbackPolicy.effectiveSlot(
            role = role,
            hasFontA = fontA != null,
            hasFontB = fontB != null,
            assignments = roleAssignments
        )
        return when (effectiveSlot) {
            FontSlot.A -> fontA
            FontSlot.B -> fontB
            null -> null
        }
    }
}
