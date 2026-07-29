package com.example.mylibrary.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.mylibrary.ui.theme.MainHeaderContentSpacing
import com.example.mylibrary.ui.theme.ScreenHorizontalPadding

@Composable
fun MainPageLayout(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    AppScreenContainer(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = ScreenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(MainHeaderContentSpacing),
            content = content
        )
    }
}
