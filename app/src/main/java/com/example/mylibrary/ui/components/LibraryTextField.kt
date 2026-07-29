package com.example.mylibrary.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.mylibrary.ui.theme.AppTheme
import com.example.mylibrary.ui.theme.CompactFieldHeight
import com.example.mylibrary.ui.theme.SurfaceRole

@Composable
fun LibraryTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    placeholder: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    textAlign: TextAlign = TextAlign.Start
) {
    val colors = AppTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Column(modifier = modifier) {
        AppThemeSurface(
            role = SurfaceRole.CARD,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = CompactFieldHeight),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(
                1.dp,
                when {
                    isError -> androidx.compose.material3.MaterialTheme.colorScheme.error
                    focused -> colors.accent
                    else -> colors.border
                }
            ),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = if (singleLine) 11.dp else 12.dp),
                singleLine = singleLine,
                minLines = minLines,
                maxLines = maxLines,
                keyboardOptions = keyboardOptions,
                textStyle = AppTheme.typography.input.copy(
                    color = colors.textPrimary,
                    textAlign = textAlign
                ),
                cursorBrush = SolidColor(colors.accent),
                interactionSource = interactionSource,
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            CompositionLocalProvider(
                                LocalContentColor provides colors.mutedText,
                                LocalTextStyle provides AppTheme.typography.input
                            ) {
                                placeholder?.invoke() ?: label()
                            }
                        }
                        innerTextField()
                    }
                }
            )
        }
        supportingText?.let {
            CompositionLocalProvider(
                LocalContentColor provides if (isError) {
                    androidx.compose.material3.MaterialTheme.colorScheme.error
                } else {
                    colors.textSecondary
                },
                LocalTextStyle provides AppTheme.typography.metadata
            ) {
                Box(modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 4.dp)) {
                    it()
                }
            }
        }
    }
}
