package com.example.mylibrary.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mylibrary.ui.theme.AppTheme

@Composable
fun ActiveFilterChip(
    text: String,
    onClear: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    Surface(
        color = colors.surfaces.card,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = modifier
                .height(28.dp)
                .noRippleClickable(onClick = onClear)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                color = colors.textSecondary,
                style = AppTheme.typography.metadata.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )

            Spacer(modifier = Modifier.width(4.dp))

            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = contentDescription,
                modifier = Modifier.size(14.dp),
                tint = colors.mutedText
            )
        }
    }
}
