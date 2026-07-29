package com.example.mylibrary.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.example.mylibrary.ui.components.LibrarySearchField
import com.example.mylibrary.ui.components.MainPageHeader
import com.example.mylibrary.ui.components.noRippleClickable
import com.example.mylibrary.ui.theme.AppTheme
import com.example.mylibrary.ui.theme.TopBarActionSize
import kotlinx.coroutines.delay

@Composable
fun LibraryTopBar(
    isSearchActive: Boolean,
    isPageVisible: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchOpen: () -> Unit,
    onSearchClose: () -> Unit,
    onPosterExport: () -> Unit,
    isPosterExporting: Boolean,
    canExportPoster: Boolean,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val colors = AppTheme.colors

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            delay(100)
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    LaunchedEffect(isPageVisible) {
        if (!isPageVisible) {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }

    BackHandler(enabled = isSearchActive) {
        onSearchClose()
        focusManager.clearFocus(force = true)
        keyboard?.hide()
    }

    if (isSearchActive) {
        LibrarySearchField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = "搜索标题、作者或导演",
            focusRequester = focusRequester,
            trailingIcon = {
                Box(
                    modifier = Modifier
                        .size(TopBarActionSize)
                        .noRippleClickable {
                            if (query.isBlank()) {
                                onSearchClose()
                            } else {
                                onQueryChange("")
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = if (query.isBlank()) "关闭搜索" else "清空搜索",
                        tint = colors.textSecondary
                    )
                }
            },
            modifier = modifier.fillMaxWidth()
        )
    } else {
        MainPageHeader(
            title = "资料库",
            modifier = modifier,
            actions = {
                Box(
                    modifier = Modifier
                        .size(TopBarActionSize)
                        .noRippleClickable(
                            enabled = canExportPoster && !isPosterExporting,
                            onClick = onPosterExport
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isPosterExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = colors.textPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.IosShare,
                            contentDescription = "导出当前封面海报",
                            tint = if (canExportPoster) {
                                colors.textPrimary
                            } else {
                                colors.mutedText
                            }
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(TopBarActionSize)
                        .noRippleClickable(onClick = onSearchOpen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "打开搜索",
                        tint = colors.textPrimary
                    )
                }
            }
        )
    }
}
