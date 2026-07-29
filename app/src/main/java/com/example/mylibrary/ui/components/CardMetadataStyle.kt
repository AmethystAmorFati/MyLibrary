package com.example.mylibrary.ui.components

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val CardMetadataFontSize = 11.sp
internal val CardMetadataLineHeight = 14.sp
internal val CardMetadataStarSize = 14.dp
internal val CardMetadataStarSpacing = 2.dp

internal fun cardMetadataTextStyle(base: TextStyle): TextStyle = base.copy(
    fontSize = CardMetadataFontSize,
    lineHeight = CardMetadataLineHeight,
    fontWeight = FontWeight.Normal
)
