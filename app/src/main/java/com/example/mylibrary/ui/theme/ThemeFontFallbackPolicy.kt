package com.example.mylibrary.ui.theme

object ThemeFontFallbackPolicy {
    fun effectiveSlot(
        role: FontRole,
        hasFontA: Boolean,
        hasFontB: Boolean,
        assignments: Map<FontRole, FontSlot>
    ): FontSlot? = when (assignments[role]) {
        FontSlot.A -> FontSlot.A.takeIf { hasFontA }
        FontSlot.B -> when {
            hasFontB -> FontSlot.B
            hasFontA -> FontSlot.A
            else -> null
        }
        null -> null
    }

    fun resolveFile(
        role: FontRole,
        fonts: ThemeFontManifest,
        assignments: Map<FontRole, FontSlot>
    ): String? = when (
        effectiveSlot(
            role = role,
            hasFontA = fonts.fontA != null,
            hasFontB = fonts.fontB != null,
            assignments = assignments
        )
    ) {
        FontSlot.A -> fonts.fontA
        FontSlot.B -> fonts.fontB
        null -> null
    }
}
