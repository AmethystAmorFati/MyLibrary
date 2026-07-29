package com.example.mylibrary.ui.navigation

object ItemRoutes {
    const val ITEM_ID = "itemId"
    const val RECORD_ID = "recordId"
    const val ADD = "items/add"
    const val DETAIL = "items/{$ITEM_ID}"
    const val EDIT = "items/{$ITEM_ID}/edit"
    const val ADD_RECORD = "items/{$ITEM_ID}/records/add"
    const val EDIT_RECORD = "items/{$ITEM_ID}/records/{$RECORD_ID}/edit"
    const val TAGS = "items/{$ITEM_ID}/tags"

    fun detail(itemId: Long): String = "items/$itemId"
    fun edit(itemId: Long): String = "items/$itemId/edit"
    fun addRecord(itemId: Long): String = "items/$itemId/records/add"
    fun editRecord(itemId: Long, recordId: Long): String =
        "items/$itemId/records/$recordId/edit"
    fun tags(itemId: Long): String = "items/$itemId/tags"
}
