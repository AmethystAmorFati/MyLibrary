package com.example.mylibrary.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.mylibrary.domain.model.DynamicFieldDefinition
import com.example.mylibrary.domain.model.FieldDataType
import com.example.mylibrary.domain.model.ItemType
import com.example.mylibrary.ui.settings.FieldManagementScreen
import com.example.mylibrary.ui.settings.FieldManagementUiState
import com.example.mylibrary.ui.theme.MyLibraryTheme
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldManagementPresentationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun fixedFieldsStayHiddenAndAddFlowOffersAllFieldTypes() {
        var created: Pair<String, FieldDataType>? = null
        render(onCreate = { name, type -> created = name to type })

        composeRule.onNodeWithText("author").assertDoesNotExist()
        composeRule.onNodeWithText("出版社").assertExists()
        composeRule.onNodeWithText("添加字段").assertExists()
        composeRule.onNodeWithTag("add_field_row").performClick()
        composeRule.onNodeWithText("添加字段").assertExists()
        composeRule.onNodeWithTag("field_type_value").assertDoesNotExist()
        FieldDataType.entries.forEach { type ->
            composeRule.onNodeWithTag("field_type_${type.name}").assertExists()
        }
        listOf("文本", "数字", "日期", "开关", "单选", "多选", "评分").forEach {
            assertTrue(
                composeRule.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty()
            )
        }
        composeRule.onNodeWithTag("field_type_NUMBER").performClick()
        composeRule.onNodeWithText("字段归属").assertExists()
        composeRule.onNodeWithTag("field_scope_ITEM").assertExists()
        composeRule.onNodeWithTag("field_scope_RECORD").assertExists()
        composeRule.onNodeWithTag("field_unit_input").assertExists()
        composeRule.onNodeWithTag("field_aggregation_SUM").assertExists()
        composeRule.onNodeWithTag("field_aggregation_AVERAGE").assertExists()
        composeRule.onNodeWithTag("field_name_input").performTextInput("页数")
        composeRule.onNodeWithTag("create_field_button").performClick()

        composeRule.runOnIdle {
            assertEquals("页数" to FieldDataType.NUMBER, created)
        }
    }

    @Test
    fun selectionFieldOptionsUseInlineEditingAndDirectDeletion() {
        var deletedOption: String? = null
        render(onDeleteOption = { _, option -> deletedOption = option })

        composeRule.onNodeWithTag("field_row_3").performClick()

        composeRule.onNodeWithText("选项管理").assertExists()
        composeRule.onNodeWithText("纸质书").assertExists()
        composeRule.onNodeWithText("电子书").assertExists()
        composeRule.onNodeWithTag("add_field_option_row").assertExists()
        composeRule.onNodeWithTag("field_option_text_纸质书").performClick()
        composeRule.onNodeWithTag("field_option_edit_input").assertExists()
        composeRule.onNodeWithTag("delete_field_option_纸质书").performClick()
        composeRule.onNodeWithText("确认删除「纸质书」？").assertDoesNotExist()

        composeRule.runOnIdle {
            assertEquals("纸质书", deletedOption)
        }
    }

    @Test
    fun addOptionExpandsAnInputInsideTheCurrentSheet() {
        render()

        composeRule.onNodeWithTag("field_row_3").performClick()
        composeRule.onNodeWithTag("add_field_option_row").performClick()

        composeRule.onNodeWithTag("field_option_add_input").assertExists()
        composeRule.onNodeWithText("选项管理").assertExists()
    }

    private fun render(
        onCreate: (String, FieldDataType) -> Unit = { _, _ -> },
        onDeleteOption: (Long, String) -> Unit = { _, _ -> }
    ) {
        composeRule.setContent {
            MyLibraryTheme {
                FieldManagementScreen(
                    state = FieldManagementUiState(
                        types = listOf(ItemType(1, "Book", 0), ItemType(2, "Movie", 1)),
                        selectedTypeId = 1,
                        fields = listOf(
                            field(
                                id = 1,
                                name = "author",
                                dataType = FieldDataType.TEXT,
                                fixed = true
                            ),
                            field(
                                id = 2,
                                name = "出版社",
                                dataType = FieldDataType.TEXT
                            ),
                            field(
                                id = 3,
                                name = "阅读方式",
                                dataType = FieldDataType.SINGLE_SELECT,
                                options = listOf("纸质书", "电子书")
                            )
                        ),
                        isLoading = false
                    ),
                    onBack = {},
                    onTypeSelected = {},
                    onCreate = { name, type, _, _, _ -> onCreate(name, type) },
                    onUpdate = { _, _ -> },
                    onDelete = {},
                    onReorder = {},
                    onAddOption = { _, _ -> },
                    onRenameOption = { _, _, _ -> },
                    onDeleteOption = onDeleteOption,
                    onReorderOptions = { _, _ -> }
                )
            }
        }
    }

    private fun field(
        id: Long,
        name: String,
        dataType: FieldDataType,
        fixed: Boolean = false,
        options: List<String> = emptyList()
    ) = DynamicFieldDefinition(
        id = id,
        typeId = 1,
        typeName = "Book",
        name = name,
        dataType = dataType,
        enabled = true,
        sortOrder = id.toInt(),
        isFixed = fixed,
        options = options
    )
}
