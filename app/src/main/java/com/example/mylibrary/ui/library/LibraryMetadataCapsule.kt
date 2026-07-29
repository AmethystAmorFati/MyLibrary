package com.example.mylibrary.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mylibrary.ui.theme.AppColors
import com.example.mylibrary.ui.theme.AppTheme
import com.example.mylibrary.ui.components.CardMetadataFontSize
import com.example.mylibrary.ui.components.CardMetadataLineHeight
import com.example.mylibrary.ui.components.cardMetadataTextStyle

internal enum class LibraryMetadataCapsuleRole {
    STATUS,
    TAG
}

internal data class LibraryMetadataContent(
    val statusName: String?,
    val tagNames: List<String>
) {
    val isEmpty: Boolean
        get() = statusName == null && tagNames.isEmpty()
}

internal fun libraryMetadataContent(
    showStatus: Boolean,
    showTags: Boolean,
    statusName: String?,
    tagNames: List<String>
): LibraryMetadataContent = LibraryMetadataContent(
    statusName = statusName?.takeIf { showStatus && it.isNotBlank() },
    tagNames = if (showTags) tagNames.filter(String::isNotBlank) else emptyList()
)

@Immutable
internal data class LibraryMetadataCapsuleVisual(
    val background: Color,
    val content: Color,
    val border: Color?
)

internal fun libraryMetadataCapsuleVisual(
    role: LibraryMetadataCapsuleRole,
    colors: AppColors
): LibraryMetadataCapsuleVisual = when (role) {
    LibraryMetadataCapsuleRole.STATUS -> LibraryMetadataCapsuleVisual(
        background = colors.accent.copy(alpha = 0.10f),
        content = colors.accent,
        border = colors.accent.copy(alpha = 0.28f)
    )
    LibraryMetadataCapsuleRole.TAG -> LibraryMetadataCapsuleVisual(
        background = Color.Transparent,
        content = colors.textSecondary,
        border = colors.border
    )
}

internal val LibraryMetadataCapsuleMaxWidth = 160.dp
internal val LibraryMetadataCapsuleFontSize = CardMetadataFontSize
internal val LibraryMetadataCapsuleLineHeight = CardMetadataLineHeight
internal val LibraryMetadataCapsuleHeight = 20.dp
internal val LibraryMetadataCapsuleHorizontalPadding = 7.dp
internal val LibraryMetadataCapsuleVerticalPadding = 1.dp
internal val LibraryMetadataCapsuleCornerRadius = 10.dp
internal val LibraryMetadataCapsuleSpacing = 4.dp

@Composable
internal fun LibraryMetadataCapsule(
    text: String,
    role: LibraryMetadataCapsuleRole,
    modifier: Modifier = Modifier
) {
    val visual = libraryMetadataCapsuleVisual(role, AppTheme.colors)
    Surface(
        modifier = modifier
            .height(LibraryMetadataCapsuleHeight)
            .widthIn(max = LibraryMetadataCapsuleMaxWidth),
        shape = RoundedCornerShape(LibraryMetadataCapsuleCornerRadius),
        color = visual.background,
        border = visual.border?.let { BorderStroke(1.dp, it) },
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier.padding(
                horizontal = LibraryMetadataCapsuleHorizontalPadding,
                vertical = LibraryMetadataCapsuleVerticalPadding
            ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = cardMetadataTextStyle(AppTheme.typography.metadata),
                color = visual.content
            )
        }
    }
}
