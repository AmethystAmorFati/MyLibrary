package com.example.mylibrary.ui.item

import com.example.mylibrary.domain.model.DynamicFieldValue
import com.example.mylibrary.domain.model.FieldDataType
import com.example.mylibrary.domain.model.ItemDetail
import com.example.mylibrary.domain.model.LibraryItem
import com.example.mylibrary.domain.model.LibraryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ItemDetailPreparationTest {
    @Test
    fun preparationPublishesVisibleFieldsAndCurrentStatusTogether() {
        val item = LibraryItem(
            id = 8L,
            typeId = 1L,
            typeName = "书籍",
            title = "作品",
            creator = "作者",
            coverPath = null,
            thumbnailPath = null,
            createdTime = 1L,
            updatedTime = 2L,
            currentStatusId = 7L,
            currentStatusName = "在读",
            latestRatingHalfStars = null
        )
        val visible = field(id = 1L, value = "人民文学出版社")
        val blank = field(id = 2L, value = "")
        val fixed = field(id = 3L, value = "固定", isFixed = true)
        val status = LibraryStatus(7L, "在读", 0, true)

        val prepared = prepareItemDetailContent(
            detail = ItemDetail(
                item = item,
                records = emptyList(),
                fields = listOf(visible, blank, fixed),
                tags = emptyList()
            ),
            statuses = listOf(status),
            quotes = emptyList()
        )

        assertEquals(listOf(visible), prepared.visibleFields)
        assertEquals(status, prepared.currentStatus)
        assertEquals(item, prepared.detail?.item)
    }

    @Test
    fun missingDetailProducesOneCompleteEmptyContentModel() {
        val prepared = prepareItemDetailContent(
            detail = null,
            statuses = emptyList(),
            quotes = emptyList()
        )

        assertNull(prepared.detail)
        assertNull(prepared.currentStatus)
        assertEquals(emptyList<DynamicFieldValue>(), prepared.visibleFields)
    }

    private fun field(
        id: Long,
        value: String,
        isFixed: Boolean = false
    ) = DynamicFieldValue(
        definitionId = id,
        name = "字段 $id",
        dataType = FieldDataType.TEXT,
        value = value,
        sortOrder = id.toInt(),
        isFixed = isFixed
    )
}
