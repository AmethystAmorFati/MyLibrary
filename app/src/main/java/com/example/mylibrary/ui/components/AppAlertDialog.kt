package com.example.mylibrary.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.mylibrary.ui.theme.SurfaceRole

@Composable
fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null
) {
    val shape = AlertDialogDefaults.shape
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier.appThemeSurfaceBackground(
            role = SurfaceRole.DIALOG,
            shape = shape
        ),
        dismissButton = dismissButton,
        title = title,
        text = text,
        shape = shape,
        containerColor = Color.Transparent
    )
}
