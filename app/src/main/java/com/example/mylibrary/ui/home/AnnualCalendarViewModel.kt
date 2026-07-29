package com.example.mylibrary.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mylibrary.domain.usecase.LibraryUseCases
import com.example.mylibrary.util.toStartOfDayMillis
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

@OptIn(ExperimentalCoroutinesApi::class)
class AnnualCalendarViewModel(
    private val useCases: LibraryUseCases,
    initialYear: Int
) : ViewModel() {
    private val year = MutableStateFlow(initialYear)

    val uiState = year
        .flatMapLatest { selectedYear ->
            useCases.observeActivities(
                LocalDate.of(selectedYear, 1, 1).toStartOfDayMillis(),
                LocalDate.of(selectedYear, 12, 31).toStartOfDayMillis()
            ).map { activities ->
                buildAnnualCalendarUiState(selectedYear, activities)
            }
        }
        .catch { error ->
            emit(
                AnnualCalendarUiState(
                    year = year.value,
                    errorMessage = error.message ?: "月历读取失败"
                )
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            AnnualCalendarUiState(initialYear)
        )

    fun previousYear() = year.update { it - 1 }
    fun nextYear() = year.update { it + 1 }
    fun showYear(value: Int) {
        year.value = value
    }
}

class AnnualCalendarViewModelFactory(
    private val useCases: LibraryUseCases,
    private val initialYear: Int
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(AnnualCalendarViewModel::class.java))
        return AnnualCalendarViewModel(useCases, initialYear) as T
    }
}
