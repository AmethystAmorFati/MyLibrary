package com.example.mylibrary.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mylibrary.data.repository.UserPreferencesRepository
import com.example.mylibrary.domain.usecase.LibraryUseCases
import com.example.mylibrary.util.toLocalDate
import com.example.mylibrary.util.toStartOfDayMillis
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

private data class CalendarState(
    val selectedActivityDate: LocalDate,
    val displayMonth: YearMonth
)

private data class TimelineVisibility(
    val recordId: Long? = null,
    val startDate: LocalDate? = null
)

private data class HomeTimelineData(
    val records: List<com.example.mylibrary.domain.model.LibraryTimelineRecord>,
    val entries: List<TimelineListEntry>
)

private data class HomeContentData(
    val activities: List<com.example.mylibrary.domain.model.LibraryActivity>,
    val timeline: HomeTimelineData,
    val showCreator: Boolean,
    val showRating: Boolean,
    val showTimelineStatus: Boolean,
    val showTimelineDuration: Boolean
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val useCases: LibraryUseCases,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {
    private val today = LocalDate.now()
    private val calendarState = MutableStateFlow(
        CalendarState(
            selectedActivityDate = today,
            displayMonth = YearMonth.from(today)
        )
    )
    private val timelineVisibility = MutableStateFlow(TimelineVisibility())
    private val calendarMode = MutableStateFlow(HomeCalendarMode.WEEK)

    private val activities = calendarState
        .map { it.displayMonth.year }
        .distinctUntilChanged()
        .flatMapLatest { year ->
            useCases.observeActivities(
                LocalDate.of(year, 1, 1).toStartOfDayMillis(),
                LocalDate.of(year, 12, 31).toStartOfDayMillis()
            )
        }

    private val timelineData =
        useCases.observeTimelineRecords(0L, Long.MAX_VALUE)
            .map { records ->
                HomeTimelineData(
                    records = records,
                    entries = TimelineCalendarCoordinator.entries(
                        TimelineCalendarCoordinator.groups(records)
                    )
                )
            }
            .flowOn(Dispatchers.Default)

    private val contentData = combine(
        activities,
        timelineData,
        preferencesRepository.libraryViewPreferences
    ) { activityRows, timeline, preferences ->
        HomeContentData(
            activities = activityRows,
            timeline = timeline,
            showCreator = preferences.timelineShowCreator,
            showRating = preferences.timelineShowRating,
            showTimelineStatus = preferences.timelineShowStatus,
            showTimelineDuration = preferences.timelineShowDuration
        )
    }

    val uiState = combine(
        calendarState,
        timelineVisibility,
        calendarMode,
        contentData
    ) { calendar, timeline, mode, content ->
        HomeUiState(
            timelineVisibleRecordId = timeline.recordId,
            timelineVisibleStartDate = timeline.startDate,
            calendarSelectedActivityDate = calendar.selectedActivityDate,
            calendarDisplayMonth = calendar.displayMonth,
            calendarMode = mode,
            activities = content.activities,
            timelineRecords = content.timeline.records,
            timelineEntries = content.timeline.entries,
            timelineShowCreator = content.showCreator,
            timelineShowRating = content.showRating,
            timelineShowStatus = content.showTimelineStatus,
            timelineShowDuration = content.showTimelineDuration,
            isInitialLoading = false
        )
    }
        .catch { error ->
            emit(
                HomeUiState(
                    timelineVisibleRecordId = timelineVisibility.value.recordId,
                    timelineVisibleStartDate = timelineVisibility.value.startDate,
                    calendarSelectedActivityDate =
                        calendarState.value.selectedActivityDate,
                    calendarDisplayMonth = calendarState.value.displayMonth,
                    calendarMode = calendarMode.value,
                    isInitialLoading = false,
                    errorMessage = error.message ?: "活动读取失败"
                )
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = HomeUiState()
        )

    fun selectDate(date: LocalDate) {
        calendarState.value = CalendarState(
            selectedActivityDate = date,
            displayMonth = YearMonth.from(date)
        )
    }

    fun selectAnnualDate(date: LocalDate) {
        selectDate(date)
        calendarMode.value = HomeCalendarMode.MONTH
    }

    fun previousMonth() = moveMonth(-1)

    fun nextMonth() = moveMonth(1)

    fun expandCalendar() {
        calendarMode.update { mode ->
            when (mode) {
                HomeCalendarMode.WEEK -> HomeCalendarMode.MONTH
                HomeCalendarMode.MONTH -> HomeCalendarMode.MONTH
            }
        }
    }

    fun showTimelineRecord(recordId: Long) {
        val record = uiState.value.timelineRecords
            .firstOrNull { it.recordId == recordId }
            ?: return
        timelineVisibility.value = TimelineVisibility(
            recordId = record.recordId,
            startDate = record.recordStartDate.toLocalDate()
        )
        val target = TimelineCalendarCoordinator.calendarTargetForRecord(
            currentDisplayMonth = calendarState.value.displayMonth,
            currentSelectedActivityDate = calendarState.value.selectedActivityDate,
            record = record
        ) ?: return
        calendarState.value = CalendarState(
            selectedActivityDate = target.selectedActivityDate,
            displayMonth = target.displayMonth
        )
    }

    fun collapseCalendar() {
        calendarMode.update { mode ->
            when (mode) {
                HomeCalendarMode.MONTH -> HomeCalendarMode.WEEK
                HomeCalendarMode.WEEK -> HomeCalendarMode.WEEK
            }
        }
    }

    private fun moveMonth(delta: Long) {
        val change = TimelineCalendarCoordinator.moveCalendarMonth(
            calendarDisplayMonth = calendarState.value.displayMonth,
            calendarSelectedActivityDate = calendarState.value.selectedActivityDate,
            delta = delta
        )
        calendarState.value = CalendarState(
            selectedActivityDate = change.calendarSelectedActivityDate,
            displayMonth = change.calendarDisplayMonth
        )
    }
}

class HomeViewModelFactory(
    private val useCases: LibraryUseCases,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(HomeViewModel::class.java))
        return HomeViewModel(useCases, preferencesRepository) as T
    }
}
