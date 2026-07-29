package com.example.mylibrary.ui.theme

import android.graphics.Typeface
import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

enum class FontSlot { A, B }

enum class FontRole(val defaultSlot: FontSlot) {
    BRAND(FontSlot.A),
    HEADING(FontSlot.A),
    CONTENT(FontSlot.B),
    META(FontSlot.B)
}

interface AppFontResolver {
    fun composeFontFamily(role: FontRole): FontFamily
    fun androidTypeface(role: FontRole): Typeface
}

object SystemAppFontResolver : AppFontResolver {
    override fun composeFontFamily(role: FontRole): FontFamily = FontFamily.Default
    override fun androidTypeface(role: FontRole): Typeface = Typeface.DEFAULT
}

@Immutable
data class AppTypography(
    val appName: TextStyle,
    val pageTitle: TextStyle,
    val sectionTitle: TextStyle,
    val cardTitle: TextStyle,
    val itemTitle: TextStyle,
    val creator: TextStyle,
    val body: TextStyle,
    val metadata: TextStyle,
    val calendarMonth: TextStyle,
    val calendarWeekday: TextStyle,
    val calendarDay: TextStyle,
    val timelineMonth: TextStyle,
    val timelineDay: TextStyle,
    val input: TextStyle,
    val button: TextStyle,
    val capsule: TextStyle
)

internal fun createAppTypography(
    resolver: AppFontResolver = SystemAppFontResolver
): AppTypography {
    val brand = resolver.composeFontFamily(FontRole.BRAND)
    val heading = resolver.composeFontFamily(FontRole.HEADING)
    val content = resolver.composeFontFamily(FontRole.CONTENT)
    val meta = resolver.composeFontFamily(FontRole.META)
    return AppTypography(
        appName = TextStyle(
            fontFamily = brand, fontWeight = FontWeight.SemiBold,
            fontSize = 30.sp, lineHeight = 36.sp
        ),
        pageTitle = TextStyle(
            fontFamily = heading, fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp, lineHeight = 26.sp
        ),
        sectionTitle = TextStyle(
            fontFamily = heading, fontWeight = FontWeight.Medium,
            fontSize = 13.sp, lineHeight = 17.sp
        ),
        cardTitle = TextStyle(
            fontFamily = heading, fontWeight = FontWeight.Medium,
            fontSize = 13.sp, lineHeight = 18.sp
        ),
        itemTitle = TextStyle(
            fontFamily = heading, fontWeight = FontWeight.Medium,
            fontSize = 14.sp, lineHeight = 18.sp
        ),
        creator = TextStyle(
            fontFamily = content, fontWeight = FontWeight.Normal,
            fontSize = 11.sp, lineHeight = 14.sp
        ),
        body = TextStyle(
            fontFamily = content, fontWeight = FontWeight.Normal,
            fontSize = 13.sp, lineHeight = 18.sp
        ),
        metadata = TextStyle(
            fontFamily = meta, fontWeight = FontWeight.Normal,
            fontSize = 11.sp, lineHeight = 14.sp
        ),
        calendarMonth = TextStyle(
            fontFamily = heading, fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp, lineHeight = 22.sp
        ),
        calendarWeekday = TextStyle(
            fontFamily = meta, fontWeight = FontWeight.Normal,
            fontSize = 11.sp, lineHeight = 14.sp
        ),
        calendarDay = TextStyle(
            fontFamily = meta, fontWeight = FontWeight.Medium,
            fontSize = 12.sp, lineHeight = 15.sp
        ),
        timelineMonth = TextStyle(
            fontFamily = heading, fontWeight = FontWeight.Medium,
            fontSize = 18.sp, lineHeight = 22.sp
        ),
        timelineDay = TextStyle(
            fontFamily = meta, fontWeight = FontWeight.Medium,
            fontSize = 12.sp, lineHeight = 15.sp
        ),
        input = TextStyle(
            fontFamily = content, fontWeight = FontWeight.Normal,
            fontSize = 13.sp, lineHeight = 18.sp
        ),
        button = TextStyle(
            fontFamily = content, fontWeight = FontWeight.Medium,
            fontSize = 13.sp, lineHeight = 18.sp
        ),
        capsule = TextStyle(
            fontFamily = meta, fontWeight = FontWeight.Medium,
            fontSize = 11.sp, lineHeight = 14.sp
        )
    )
}

internal fun AppTypography.toMaterialTypography() = Typography(
    displayLarge = appName,
    displayMedium = appName,
    displaySmall = appName,
    headlineLarge = pageTitle,
    headlineMedium = pageTitle,
    headlineSmall = pageTitle,
    titleLarge = pageTitle,
    titleMedium = sectionTitle,
    titleSmall = cardTitle,
    bodyLarge = body,
    bodyMedium = creator,
    bodySmall = metadata,
    labelLarge = button,
    labelMedium = capsule,
    labelSmall = metadata
)
