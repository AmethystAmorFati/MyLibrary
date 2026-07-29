package com.example.mylibrary.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.mylibrary.di.AppContainer
import com.example.mylibrary.domain.model.LibraryViewPreferences
import com.example.mylibrary.ui.home.HomeScreen
import com.example.mylibrary.ui.home.HomeViewModel
import com.example.mylibrary.ui.home.HomeViewModelFactory
import com.example.mylibrary.ui.library.LibraryScreen
import com.example.mylibrary.ui.library.LibraryViewModel
import com.example.mylibrary.ui.library.LibraryViewModelFactory
import com.example.mylibrary.ui.settings.SettingsScreen
import com.example.mylibrary.ui.settings.SettingsViewModel
import com.example.mylibrary.ui.settings.SettingsViewModelFactory
import com.example.mylibrary.ui.statistics.StatisticsScreen
import com.example.mylibrary.ui.statistics.StatisticsViewModel
import com.example.mylibrary.ui.statistics.StatisticsViewModelFactory
import com.example.mylibrary.ui.quote.QuoteRoutes
import com.example.mylibrary.ui.theme.AppTheme

@Composable
fun MainPagerScreen(
    navController: NavHostController,
    container: AppContainer,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val navigationIconResolver = AppTheme.navigationIconResolver
    val homeViewModel: HomeViewModel = viewModel(
        key = "main_home",
        factory = HomeViewModelFactory(
            container.libraryUseCases,
            container.userPreferencesRepository
        )
    )
    val homeState by homeViewModel.uiState.collectAsState()
    val statisticsViewModel: StatisticsViewModel = viewModel(
        key = "main_statistics",
        factory = StatisticsViewModelFactory(
            container.quoteUseCases,
            container.observeCustomFieldStatistics
        )
    )
    val statisticsState by statisticsViewModel.uiState.collectAsState()
    val displayPreferences by container.userPreferencesRepository
        .libraryViewPreferences
        .collectAsState(initial = LibraryViewPreferences())

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        MainTabSlideHost(
            targetTab = selectedTab,
            modifier = Modifier.fillMaxSize()
        ) { tab ->
            when (tab) {
                0 -> HomeScreen(
                    state = homeState,
                    onDateSelected = homeViewModel::selectDate,
                    onPreviousMonth = homeViewModel::previousMonth,
                    onNextMonth = homeViewModel::nextMonth,
                    onExpandCalendar = homeViewModel::expandCalendar,
                    onCollapseCalendar = homeViewModel::collapseCalendar,
                    onOpenAnnualCalendar = {
                        navController.navigate(
                            HomeRoutes.annual(
                                year = homeState.calendarDisplayMonth.year,
                                month = homeState.calendarDisplayMonth.monthValue
                            )
                        )
                    },
                    onTimelineRecordChanged = homeViewModel::showTimelineRecord,
                    onItemSelected = { navController.navigate(ItemRoutes.detail(it)) }
                )
                1 -> {
                    val libraryViewModel: LibraryViewModel = viewModel(
                        key = "main_library",
                        factory = LibraryViewModelFactory(
                            container.libraryUseCases,
                            container.tagUseCases,
                            container.fieldUseCases,
                            container.userPreferencesRepository
                        )
                    )
                    val libraryState by libraryViewModel.uiState.collectAsState()
                    LibraryScreen(
                        uiState = libraryState,
                        onQueryChange = libraryViewModel::onQueryChange,
                        onSearchOpen = libraryViewModel::openSearch,
                        onSearchClose = libraryViewModel::closeSearch,
                        onStatusSelected = libraryViewModel::onStatusSelected,
                        onTagsSelected = libraryViewModel::onTagsSelected,
                        onViewModeSelected = libraryViewModel::setViewMode,
                        onListFieldsChanged = libraryViewModel::setListDisplayFields,
                        onItemSelected = { navController.navigate(ItemRoutes.detail(it)) },
                        isPageVisible = selectedTab == 1
                    )
                }
                2 -> {
                    StatisticsScreen(
                        state = statisticsState,
                        showQuoteChapter = displayPreferences.showQuoteChapter,
                        showQuotePage = displayPreferences.showQuotePage,
                        onQuoteSelected = {
                            navController.navigate(ItemRoutes.detail(it))
                        },
                        onViewAllQuotes = {
                            navController.navigate(QuoteRoutes.LIST)
                        }
                    )
                }
                3 -> {
                    val settingsViewModel: SettingsViewModel = viewModel(
                        key = "main_settings",
                        factory = SettingsViewModelFactory(
                            container.libraryUseCases,
                            container.fieldUseCases,
                            container.backupRepository,
                            container.reportDataResolver
                        )
                    )
                    val settingsState by settingsViewModel.uiState.collectAsState()
                    SettingsScreen(
                        state = settingsState,
                        onLayoutSettings = {
                            navController.navigate(SettingsRoutes.LAYOUT)
                        },
                        onFieldManagement = {
                            navController.navigate(SettingsRoutes.FIELDS)
                        },
                        onTagManagement = { navController.navigate(SettingsRoutes.TAGS) },
                        onStatusManagement = {
                            navController.navigate(SettingsRoutes.STATUSES)
                        },
                        onTrash = {
                            navController.navigate(SettingsRoutes.TRASH)
                        },
                        onExportData = settingsViewModel::exportData,
                        onPrepareReport = settingsViewModel::prepareReport,
                        onImportFileSelected = settingsViewModel::prepareImport,
                        onConfirmImport = settingsViewModel::confirmImport,
                        onCancelImport = settingsViewModel::cancelPreparedImport,
                        onBackupMessageShown = settingsViewModel::consumeBackupMessage
                    )
                }
            }
        }
        LibraryBottomBar(
            selectedTab = selectedTab,
            onNavigate = { selectedTab = it },
            onAddItem = { navController.navigate(ItemRoutes.ADD) },
            iconResolver = navigationIconResolver,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
