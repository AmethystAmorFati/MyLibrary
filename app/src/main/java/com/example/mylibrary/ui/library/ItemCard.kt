package com.example.mylibrary.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mylibrary.domain.model.LibraryItem
import com.example.mylibrary.ui.components.CoverDisplayMode
import com.example.mylibrary.ui.components.CoverImage
import com.example.mylibrary.ui.components.noRippleClickable
import com.example.mylibrary.ui.theme.AppTheme
import com.example.mylibrary.ui.theme.CoverAspectRatio

internal object LibraryGridTitlePolicy {
    const val maxLines = 2
    val textAlign = TextAlign.Center
}

@Composable
fun ItemCard(
    item: LibraryItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .testTag("item_card_${item.id}")
            .noRippleClickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        CoverImage(
            thumbnailPath = item.thumbnailPath,
            originalPath = item.coverPath,
            title = item.title,
            creator = item.creator,
            typeName = item.typeName,
            typeId = item.typeId,
            displayMode = CoverDisplayMode.LIBRARY_GRID,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(CoverAspectRatio)
        )
        Text(
            text = item.title,
            modifier = Modifier.fillMaxWidth(),
            minLines = LibraryGridTitlePolicy.maxLines,
            maxLines = LibraryGridTitlePolicy.maxLines,
            textAlign = LibraryGridTitlePolicy.textAlign,
            overflow = TextOverflow.Ellipsis,
            style = AppTheme.typography.itemTitle.copy(
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Normal
            ),
            color = AppTheme.colors.textPrimary
        )
    }
}
