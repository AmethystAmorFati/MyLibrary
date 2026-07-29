package com.example.mylibrary.domain.model

data class LibraryStatus(
    val id: Long,
    val name: String,
    val sortOrder: Int,
    val enabled: Boolean,
    val scope: StatusScope = StatusScope.ITEM
)
