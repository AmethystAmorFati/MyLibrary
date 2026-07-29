package com.example.mylibrary.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mylibrary.domain.model.DynamicFieldDefinition
import com.example.mylibrary.domain.model.LibraryDisplayFieldKey
import com.example.mylibrary.ui.components.AppCapsule
import com.example.mylibrary.ui.components.AppModalBottomSheet
import com.example.mylibrary.ui.components.noRippleClickable
import com.example.mylibrary.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListFieldConfigSheet(
    selectedFields: Set<String>,
    dynamicFields: List<DynamicFieldDefinition>,
    onSave: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
    dynamicFieldsOnly: Boolean = false
) {
    var selected by remember(selectedFields) { mutableStateOf(selectedFields) }
    val options = buildList {
        if (!dynamicFieldsOnly) {
            add(LibraryDisplayFieldKey.CURRENT_STATUS to "当前状态")
            add(LibraryDisplayFieldKey.TAGS to "标签")
        }
        dynamicFields.filter { it.enabled && !it.isFixed }.forEach {
            add(LibraryDisplayFieldKey.dynamic(it.id) to it.name)
        }
    }
    val colors = AppTheme.colors

    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .noRippleClickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "关闭",
                        tint = colors.textPrimary
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (dynamicFieldsOnly) "自定义字段" else "列表展示字段",
                        style = AppTheme.typography.sectionTitle,
                        color = colors.textPrimary
                    )
                    Text(
                        if (dynamicFieldsOnly) {
                            "选择需要显示在列表卡片中的字段"
                        } else {
                            "作者 / 导演默认显示"
                        },
                        color = colors.mutedText,
                        style = AppTheme.typography.metadata
                    )
                }
                Text(
                    "完成",
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 10.dp)
                        .noRippleClickable {
                            onSave(selected)
                            onDismiss()
                        },
                    style = AppTheme.typography.button,
                    color = colors.textPrimary
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { (key, label) ->
                    val isSelected = key in selected
                    AppCapsule(
                        text = label,
                        selected = isSelected,
                        enabled = true,
                        onClick = {
                            selected = if (isSelected) selected - key else selected + key
                        }
                    )
                }
            }
        }
    }
}
