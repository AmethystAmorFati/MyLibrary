package com.example.mylibrary.ui.home

import com.example.mylibrary.domain.model.LibraryActivity
import com.example.mylibrary.domain.model.LibraryTimelineRecord
import java.time.LocalDate
import java.time.YearMonth

enum class HomeCalendarMode {
    WEEK,
    MONTH
}

data class HomeUiState(
    val timelineVisibleRecordId: Long? = null,
    val timelineVisibleStartDate: LocalDate? = null,
    val calendarSelectedActivityDate: LocalDate = LocalDate.now(),
    val calendarDisplayMonth: YearMonth = YearMonth.now(),
    val calendarMode: HomeCalendarMode = HomeCalendarMode.WEEK,
    val activities: List<LibraryActivity> = emptyList(),
    val timelineRecords: List<LibraryTimelineRecord> = emptyList(),
    val timelineEntries: List<TimelineListEntry> = emptyList(),
    val timelineShowCreator: Boolean = false,
    val timelineShowRating: Boolean = false,
    val timelineShowStatus: Boolean = false,
    val timelineShowDuration: Boolean = true,
    val isInitialLoading: Boolean = true,
    val errorMessage: String? = null
)
