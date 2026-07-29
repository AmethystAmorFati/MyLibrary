package com.example.mylibrary.ui.home

import com.example.mylibrary.domain.model.LibraryActivity

/** Returns the latest four distinct item covers in stable visual priority order. */
internal fun orderedActivitiesForCoverStack(
    activities: List<LibraryActivity>
): List<LibraryActivity> = activities
    .sortedWith(
        compareByDescending<LibraryActivity> { it.recordCreatedAt }
            .thenByDescending { it.recordId ?: Long.MIN_VALUE }
            .thenByDescending { it.id }
    )
    .distinctBy(LibraryActivity::itemId)
    .take(4)
