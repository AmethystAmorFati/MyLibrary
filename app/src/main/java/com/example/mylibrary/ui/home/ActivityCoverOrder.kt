package com.example.mylibrary.ui.home

import com.example.mylibrary.domain.model.LibraryActivity
import com.example.mylibrary.domain.model.orderedDistinctActivityCovers

/** Returns the latest four distinct item covers in stable visual priority order. */
internal fun orderedActivitiesForCoverStack(
    activities: List<LibraryActivity>
): List<LibraryActivity> = orderedDistinctActivityCovers(
    activities = activities,
    recordCreatedAt = LibraryActivity::recordCreatedAt,
    recordId = LibraryActivity::recordId,
    activityId = LibraryActivity::id,
    itemId = LibraryActivity::itemId
)
