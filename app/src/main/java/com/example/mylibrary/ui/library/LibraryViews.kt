package com.example.mylibrary.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mylibrary.domain.model.DynamicFieldDefinition
import com.example.mylibrary.domain.model.LibraryDisplayFieldKey
import com.example.mylibrary.domain.model.LibraryItem
import com.example.mylibrary.domain.model.LibraryViewMode
import com.example.mylibrary.domain.model.ItemTypeKind
import com.example.mylibrary.ui.components.CoverDisplayMode
import com.example.mylibrary.ui.components.AppThemeSurface
import com.example.mylibrary.ui.components.CoverImage
import com.example.mylibrary.ui.components.cardMetadataTextStyle
import com.example.mylibrary.ui.components.noRippleClickable
import com.example.mylibrary.ui.theme.AppTheme
import com.example.mylibrary.ui.theme.BottomContentPadding
import com.example.mylibrary.ui.theme.CoverGridSpacing
import com.example.mylibrary.ui.theme.CardContentPadding
import com.example.mylibrary.ui.theme.LibraryShapes
import com.example.mylibrary.ui.theme.LibraryListCoverHeight
import com.example.mylibrary.ui.theme.LibraryListCoverWidth
import com.example.mylibrary.ui.theme.ShelfGridSpacing
import com.example.mylibrary.ui.theme.SurfaceRole
import com.example.mylibrary.util.formatDuration
import com.example.mylibrary.util.totalDurationLabel

@Composable
fun LibraryItemsView(
    items: List<LibraryItem>,
    mode: LibraryViewMode,
    gridColumns: Int,
    coverColumns: Int,
    displayFields: Set<String>,
    dynamicFields: List<DynamicFieldDefinition>,
    showTotalDuration: Boolean = true,
    onItemSelected: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val shelfState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val coverState = rememberLazyGridState()
    when (mode) {
        LibraryViewMode.SHELF -> ShelfView(
            items = items,
            columns = gridColumns,
            state = shelfState,
            onItemSelected = onItemSelected,
            modifier = modifier
        )
        LibraryViewMode.LIST -> ListView(
            items = items,
            displayFields = displayFields,
            dynamicFields = dynamicFields,
            showTotalDuration = showTotalDuration,
            state = listState,
            onItemSelected = onItemSelected,
            modifier = modifier
        )
        LibraryViewMode.COVER -> CoverOnlyView(
            items = items,
            columns = coverColumns,
            state = coverState,
            onItemSelected = onItemSelected,
            modifier = modifier
        )
    }
}

@Composable
private fun ShelfView(
    items: List<LibraryItem>,
    columns: Int,
    state: LazyGridState,
    onItemSelected: (Long) -> Unit,
    modifier: Modifier
) {
    LazyVerticalGrid(
        state = state,
        columns = GridCells.Fixed(columns.coerceIn(2, 6)),
        modifier = modifier.testTag("library_shelf_view"),
        horizontalArrangement = Arrangement.spacedBy(ShelfGridSpacing),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = BottomContentPadding)
    ) {
        items(items, key = ::libraryItemKey) { item ->
            ItemCard(item = item, onClick = { onItemSelected(item.id) })
        }
    }
}

@Composable
private fun CoverOnlyView(
    items: List<LibraryItem>,
    columns: Int,
    state: LazyGridState,
    onItemSelected: (Long) -> Unit,
    modifier: Modifier
) {
    LazyVerticalGrid(
        state = state,
        columns = GridCells.Fixed(columns.coerceIn(2, 6)),
        modifier = modifier.testTag("library_cover_view"),
        horizontalArrangement = Arrangement.spacedBy(CoverGridSpacing),
        verticalArrangement = Arrangement.spacedBy(CoverGridSpacing),
        contentPadding = PaddingValues(bottom = BottomContentPadding)
    ) {
        items(items, key = ::libraryItemKey) { item ->
            Box(
                modifier = Modifier
                    .testTag("library_cover_only_item_${item.id}")
                    .noRippleClickable { onItemSelected(item.id) }
            ) {
                CoverImage(
                    thumbnailPath = item.thumbnailPath,
                    originalPath = item.coverPath,
                    title = item.title,
                    creator = item.creator,
                    typeName = item.typeName,
                    typeId = item.typeId,
                    displayMode = CoverDisplayMode.LIBRARY_COVER_ONLY,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(com.example.mylibrary.ui.theme.CoverAspectRatio)
                )
            }
        }
    }
}

@Composable
private fun ListView(
    items: List<LibraryItem>,
    displayFields: Set<String>,
    dynamicFields: List<DynamicFieldDefinition>,
    showTotalDuration: Boolean,
    state: LazyListState,
    onItemSelected: (Long) -> Unit,
    modifier: Modifier
) {
    val fieldNames = dynamicFields.associate { it.id to it.name }
    LazyColumn(
        state = state,
        modifier = modifier.testTag("library_list_view"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = BottomContentPadding)
    ) {
        items(items, key = ::libraryItemKey) { item ->
            AppThemeSurface(
                role = SurfaceRole.CARD,
                modifier = Modifier
                    .fillMaxWidth()
                    .noRippleClickable { onItemSelected(item.id) }
                    .testTag("item_card_${item.id}"),
                shape = LibraryShapes.medium,
                shadowElevation = 0.dp,
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(CardContentPadding),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CoverImage(
                        thumbnailPath = item.thumbnailPath,
                        originalPath = item.coverPath,
                        title = item.title,
                        creator = item.creator,
                        typeName = item.typeName,
                        typeId = item.typeId,
                        displayMode = CoverDisplayMode.LIBRARY_LIST,
                        modifier = Modifier
                            .size(
                                width = LibraryListCoverWidth,
                                height = LibraryListCoverHeight
                            )
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            item.title,
                            modifier = Modifier.testTag(
                                "item_card_${item.id}_title"
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = AppTheme.typography.itemTitle,
                            color = AppTheme.colors.textPrimary
                        )
                        item.creatorLine()?.let { line ->
                            ListMetadataText(
                                text = line,
                                modifier = Modifier.testTag(
                                    "item_card_${item.id}_creator"
                                )
                            )
                        }
                        val metadata = libraryMetadataContent(
                            showStatus =
                                LibraryDisplayFieldKey.CURRENT_STATUS in displayFields,
                            showTags = LibraryDisplayFieldKey.TAGS in displayFields,
                            statusName = item.currentStatusName,
                            tagNames = item.tagNames
                        )
                        LibraryStatusAndTagsRow(
                            itemId = item.id,
                            statusName = metadata.statusName,
                            tagNames = metadata.tagNames
                        )
                        item.dynamicDisplayLines(displayFields, fieldNames)
                            .forEachIndexed { index, line ->
                                ListMetadataText(
                                    text = line,
                                    modifier = Modifier.testTag(
                                        "item_card_${item.id}_dynamic_$index"
                                    )
                                )
                            }
                        if (showTotalDuration) {
                            item.totalDurationMinutes
                                ?.let(::formatDuration)
                                ?.let { duration ->
                                    ListMetadataText(
                                        text = "${totalDurationLabel(item.typeId)} $duration",
                                        modifier = Modifier.testTag(
                                            "item_card_${item.id}_total_duration"
                                        )
                                    )
                                }
                        }
                    }
                }
            }
        }
    }
}

internal fun libraryItemKey(item: LibraryItem): Long = item.id

@Composable
private fun ListMetadataText(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text,
        modifier = modifier,
        color = AppTheme.colors.textSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = cardMetadataTextStyle(AppTheme.typography.metadata)
    )
}

private fun LibraryItem.creatorLine(): String? =
    creator.takeIf(String::isNotBlank)?.let {
        "${if (typeId == ItemTypeKind.MOVIE_TYPE_ID) "导演" else "作者"} · $it"
    }

@Composable
private fun LibraryStatusAndTagsRow(
    itemId: Long,
    statusName: String?,
    tagNames: List<String>
) {
    if (statusName == null && tagNames.isEmpty()) return

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("item_card_${itemId}_metadata")
    ) {
        val statusMaxWidth = maxWidth / 2
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(LibraryMetadataCapsuleSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            statusName?.let {
                LibraryMetadataCapsule(
                    text = it,
                    role = LibraryMetadataCapsuleRole.STATUS,
                    modifier = Modifier
                        .widthIn(max = statusMaxWidth)
                        .testTag("item_card_${itemId}_status")
                )
            }
            if (tagNames.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("item_card_${itemId}_tags"),
                    horizontalArrangement =
                        Arrangement.spacedBy(LibraryMetadataCapsuleSpacing)
                ) {
                    itemsIndexed(tagNames) { index, tagName ->
                        LibraryMetadataCapsule(
                            text = tagName,
                            role = LibraryMetadataCapsuleRole.TAG,
                            modifier = Modifier.testTag(
                                "item_card_${itemId}_tag_$index"
                            )
                        )
                    }
                }
            }
        }
    }
}

private fun LibraryItem.dynamicDisplayLines(
    keys: Set<String>,
    fieldNames: Map<Long, String>
): List<String> = keys.mapNotNull { key ->
    LibraryDisplayFieldKey.dynamicId(key)?.let { id ->
            dynamicValues[id]?.takeIf(String::isNotBlank)?.let { value ->
                "${fieldNames[id] ?: "字段"} $value"
            }
    }
}
