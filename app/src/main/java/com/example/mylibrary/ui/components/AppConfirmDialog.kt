package com.example.mylibrary.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.mylibrary.ui.theme.AppDanger
import com.example.mylibrary.ui.theme.AppTheme
import com.example.mylibrary.ui.theme.LibraryShapes
import com.example.mylibrary.ui.theme.SurfaceRole

@Composable
fun AppConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    confirmTestTag: String? = null
) {
    Dialog(onDismissRequest = onDismiss) {
        AppThemeSurface(
            role = SurfaceRole.DIALOG,
            modifier = Modifier.fillMaxWidth(0.86f),
            shape = LibraryShapes.large,
            shadowElevation = 0.dp,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(
                    start = 24.dp,
                    top = 24.dp,
                    end = 24.dp,
                    bottom = 18.dp
                )
            ) {
                Text(
                    text = title,
                    style = AppTheme.typography.pageTitle,
                    color = AppTheme.colors.textPrimary
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = message,
                    style = AppTheme.typography.body,
                    color = AppTheme.colors.textSecondary
                )
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    DialogAction(
                        text = dismissText,
                        color = AppTheme.colors.textSecondary,
                        onClick = onDismiss
                    )
                    Spacer(Modifier.width(18.dp))
                    DialogAction(
                        text = confirmText,
                        color = if (destructive) AppDanger else AppTheme.colors.accent,
                        onClick = onConfirm,
                        testTag = confirmTestTag
                    )
                }
            }
        }
    }
}

@Composable
private fun DialogAction(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    testTag: String? = null
) {
    Text(
        text = text,
        modifier = Modifier
            .padding(horizontal = 6.dp, vertical = 10.dp)
            .then(testTag?.let { Modifier.testTag(it) } ?: Modifier)
            .noRippleClickable(onClick = onClick),
        style = AppTheme.typography.button,
        color = color
    )
}
