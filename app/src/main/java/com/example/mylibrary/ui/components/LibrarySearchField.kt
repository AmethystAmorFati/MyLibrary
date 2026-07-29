package com.example.mylibrary.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.example.mylibrary.ui.theme.AppTheme
import com.example.mylibrary.ui.theme.CompactFieldHeight
import com.example.mylibrary.ui.theme.SurfaceRole

@Composable
fun LibrarySearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    onFocusChanged: (Boolean) -> Unit = {}
) {
    val colors = AppTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    AppThemeSurface(
        role = SurfaceRole.CARD,
        modifier = modifier.heightIn(min = CompactFieldHeight),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (focused) colors.accent else colors.border),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = colors.mutedText,
                modifier = Modifier.size(20.dp)
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (focusRequester != null) Modifier.focusRequester(focusRequester)
                        else Modifier
                    )
                    .onFocusChanged { onFocusChanged(it.isFocused) }
                    .padding(horizontal = 9.dp, vertical = 11.dp),
                singleLine = true,
                textStyle = AppTheme.typography.input.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                interactionSource = interactionSource,
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = colors.mutedText,
                                style = AppTheme.typography.input
                            )
                        }
                        innerTextField()
                    }
                }
            )
            trailingIcon?.invoke()
        }
    }
}
