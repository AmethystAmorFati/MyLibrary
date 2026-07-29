package com.example.mylibrary.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.mylibrary.ui.theme.AppTheme
import com.example.mylibrary.ui.theme.TopBarActionSize
import com.example.mylibrary.ui.theme.TopBarHorizontalPadding
import com.example.mylibrary.ui.theme.SecondaryHeaderHeight

@Composable
fun SecondaryPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(SecondaryHeaderHeight)
            .padding(horizontal = TopBarHorizontalPadding),
        contentAlignment = Alignment.Center
    ) {
        onBack?.let {
            IconButton(
                onClick = it,
                modifier = Modifier
                    .size(TopBarActionSize)
                    .align(Alignment.CenterStart)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                    tint = AppTheme.colors.textPrimary
                )
            }
        }
        Text(
            text = title,
            style = AppTheme.typography.pageTitle,
            color = AppTheme.colors.textPrimary
        )
    }
}
