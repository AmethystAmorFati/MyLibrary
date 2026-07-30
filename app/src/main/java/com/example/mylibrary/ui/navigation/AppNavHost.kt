package com.example.mylibrary.ui.navigation

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.mylibrary.di.AppContainer
import com.example.mylibrary.domain.model.LibraryViewPreferences
import com.example.mylibrary.ui.item.AddItemScreen
import com.example.mylibrary.ui.item.EditItemScreen
import com.example.mylibrary.ui.item.ItemDetailScreen
import com.example.mylibrary.ui.item.ItemDetailViewModel
import com.example.mylibrary.ui.item.ItemDetailViewModelFactory
import com.example.mylibrary.ui.item.ItemEditorViewModel
import com.example.mylibrary.ui.item.ItemEditorViewModelFactory
import com.example.mylibrary.ui.item.ItemTagEditorScreen
import com.example.mylibrary.ui.item.ItemTagEditorViewModel
import com.example.mylibrary.ui.item.ItemTagEditorViewModelFactory
import com.example.mylibrary.ui.quote.QuoteListScreen
import com.example.mylibrary.ui.quote.QuoteListViewModel
import com.example.mylibrary.ui.quote.QuoteListViewModelFactory
import com.example.mylibrary.ui.quote.QuoteRoutes
import com.example.mylibrary.ui.home.AnnualCalendarScreen
import com.example.mylibrary.ui.home.AnnualCalendarViewModel
import com.example.mylibrary.ui.home.AnnualCalendarViewModelFactory
import com.example.mylibrary.ui.home.HomeViewModel
import com.example.mylibrary.ui.home.HomeViewModelFactory
import com.example.mylibrary.ui.settings.FieldManagementScreen
import com.example.mylibrary.ui.settings.FieldManagementViewModel
import com.example.mylibrary.ui.settings.FieldManagementViewModelFactory
import com.example.mylibrary.ui.settings.LayoutSettingsScreen
import com.example.mylibrary.ui.settings.LayoutSettingsViewModel
import com.example.mylibrary.ui.settings.LayoutSettingsViewModelFactory
import com.example.mylibrary.ui.settings.ItemTypeManagementScreen
import com.example.mylibrary.ui.settings.ItemTypeManagementViewModel
import com.example.mylibrary.ui.settings.ItemTypeManagementViewModelFactory
import com.example.mylibrary.ui.settings.StatusManagementScreen
import com.example.mylibrary.ui.settings.StatusManagementViewModel
import com.example.mylibrary.ui.settings.StatusManagementViewModelFactory
import com.example.mylibrary.ui.settings.TagManagementScreen
import com.example.mylibrary.ui.settings.TagManagementViewModel
import com.example.mylibrary.ui.settings.TagManagementViewModelFactory
import com.example.mylibrary.ui.settings.ThemeManagementScreen
import com.example.mylibrary.ui.settings.ThemeManagementViewModel
import com.example.mylibrary.ui.settings.ThemeManagementViewModelFactory
import com.example.mylibrary.ui.settings.TrashScreen
import com.example.mylibrary.ui.settings.TrashViewModel
import com.example.mylibrary.ui.settings.TrashViewModelFactory

@Composable
fun AppNavHost(
    navController: NavHostController,
    container: AppContainer,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = MainRoutes.ROOT,
        modifier = modifier,
        enterTransition = AppNavigationTransitions.enter,
        exitTransition = AppNavigationTransitions.exit,
        popEnterTransition = AppNavigationTransitions.popEnter,
        popExitTransition = AppNavigationTransitions.popExit
    ) {
        composable(MainRoutes.ROOT) {
            MainPagerScreen(navController, container)
        }
        composable(ItemRoutes.ADD) { entry ->
            val destinationEnterCompleted =
                rememberDestinationEnterCompleted(entry)
            val viewModel: ItemEditorViewModel = viewModel(
                factory = ItemEditorViewModelFactory(
                    container.libraryUseCases,
                    container.fieldUseCases,
                    container.tagUseCases,
                    container.coverImageUseCases,
                    container.quoteUseCases,
                    itemId = null
                )
            )
            val state by viewModel.uiState.collectAsState()
            val displayPreferences by container.userPreferencesRepository
                .libraryViewPreferences
                .collectAsState(initial = LibraryViewPreferences())
            AddItemScreen(
                state = state,
                destinationEnterCompleted = destinationEnterCompleted,
                showQuoteChapter = displayPreferences.showQuoteChapter,
                showQuotePage = displayPreferences.showQuotePage,
                onBack = {
                    viewModel.discardChanges { navController.popBackStack() }
                },
                onSaved = { itemId ->
                    navController.navigate(ItemRoutes.detail(itemId)) {
                        popUpTo(ItemRoutes.ADD) { inclusive = true }
                    }
                },
                onTypeSelected = viewModel::onTypeSelected,
                onTitleChange = viewModel::onTitleChange,
                onCreatorChange = viewModel::onCreatorChange,
                onCoverSelected = viewModel::selectCover,
                onRemoveCover = viewModel::removeCover,
                onStatusSelected = viewModel::onStatusSelected,
                onTagSelectionChanged = viewModel::onTagSelectionChange,
                onCreateTag = viewModel::createTag,
                onDynamicValueChange = viewModel::onDynamicValueChange,
                onRecordDraftCompleted = viewModel::onRecordDraftCompleted,
                onRecordDraftDeleted = viewModel::onRecordDraftDeleted,
                onCreateQuoteDraft = viewModel::createQuoteDraft,
                onQuoteDraftCompleted = viewModel::onQuoteDraftCompleted,
                onQuoteDraftDeleted = viewModel::onQuoteDraftDeleted,
                onSave = viewModel::save
            )
        }
        itemDetailRoute(navController, container)
        itemEditRoute(navController, container)
        quoteListRoute(navController, container)
        itemTagRoute(navController, container)
        annualCalendarRoute(navController, container)
        fieldManagementRoute(navController, container)
        layoutSettingsRoute(navController, container)
        themeManagementRoute(container)
        tagManagementRoute(navController, container)
        statusManagementRoute(navController, container)
        itemTypeManagementRoute(navController, container)
        trashRoute(navController, container)
    }
}

private fun androidx.navigation.NavGraphBuilder.themeManagementRoute(
    container: AppContainer
) {
    composable(SettingsRoutes.THEMES) {
        val viewModel: ThemeManagementViewModel = viewModel(
            factory = ThemeManagementViewModelFactory(
                importer = container.themePackageImporter,
                catalog = container.installedThemeCatalog,
                repository = container.themeRepository,
                sourceFactory = container.themePackageSourceFactory
            )
        )
        val state by viewModel.uiState.collectAsState()
        ThemeManagementScreen(
            state = state,
            onImportSelected = { uri ->
                viewModel.importTheme(uri)
            },
            onApplyTheme = viewModel::applyTheme,
            onDeleteTheme = viewModel::deleteTheme,
            onMessageShown = viewModel::consumeMessage,
            onConfirmReplace = viewModel::confirmReplaceTheme,
            onCancelReplace = viewModel::cancelReplaceTheme
        )
    }
}

private fun androidx.navigation.NavGraphBuilder.trashRoute(
    navController: NavHostController,
    container: AppContainer
) {
    composable(SettingsRoutes.TRASH) {
        val viewModel: TrashViewModel = viewModel(
            factory = TrashViewModelFactory(container.trashUseCases)
        )
        val state by viewModel.uiState.collectAsState()
        TrashScreen(
            state = state,
            onBack = { navController.popBackStack() },
            onRestore = viewModel::restore,
            onStartSelection = viewModel::startSelection,
            onToggleSelection = viewModel::toggleSelection,
            onClearSelection = viewModel::clearSelection,
            onPermanentlyDeleteSelected = viewModel::permanentlyDeleteSelected,
            onEmptyTrash = viewModel::emptyTrash
        )
    }
}

private fun androidx.navigation.NavGraphBuilder.annualCalendarRoute(
    navController: NavHostController,
    container: AppContainer
) {
    composable(
        route = HomeRoutes.ANNUAL,
        arguments = listOf(
            navArgument(HomeRoutes.YEAR) { type = NavType.IntType },
            navArgument(HomeRoutes.MONTH) { type = NavType.IntType }
        )
    ) { entry ->
        val initialYear = requireNotNull(entry.arguments?.getInt(HomeRoutes.YEAR))
        val initialMonth = requireNotNull(entry.arguments?.getInt(HomeRoutes.MONTH))
            .coerceIn(1, 12)
        val rootEntry = remember(entry) {
            navController.getBackStackEntry(MainRoutes.ROOT)
        }
        val homeViewModel: HomeViewModel = viewModel(
            viewModelStoreOwner = rootEntry,
            key = "main_home",
            factory = HomeViewModelFactory(
                container.libraryUseCases,
                container.userPreferencesRepository
            )
        )
        val homeState by homeViewModel.uiState.collectAsState()
        val annualViewModel: AnnualCalendarViewModel = viewModel(
            factory = AnnualCalendarViewModelFactory(
                container.libraryUseCases,
                initialYear
            )
        )
        val annualState by annualViewModel.uiState.collectAsState()
        AnnualCalendarScreen(
            state = annualState,
            initialYear = initialYear,
            initialMonth = initialMonth,
            selectedDate = homeState.calendarSelectedActivityDate,
            onBack = { navController.popBackStack() },
            onPreviousYear = annualViewModel::previousYear,
            onNextYear = annualViewModel::nextYear,
            onDateSelected = { date ->
                homeViewModel.selectAnnualDate(date)
                navController.popBackStack()
            }
        )
    }
}

private fun androidx.navigation.NavGraphBuilder.itemDetailRoute(
    navController: NavHostController,
    container: AppContainer
) {
    composable(
        route = ItemRoutes.DETAIL,
        arguments = listOf(navArgument(ItemRoutes.ITEM_ID) { type = NavType.LongType })
    ) { entry ->
        val itemId = requireNotNull(entry.arguments?.getLong(ItemRoutes.ITEM_ID))
        val viewModel: ItemDetailViewModel = viewModel(
            factory = ItemDetailViewModelFactory(
                container.libraryUseCases,
                container.quoteUseCases,
                itemId
            )
        )
        val destinationEnterCompleted =
            rememberDestinationEnterCompleted(entry)
        val state by viewModel.uiState.collectAsState()
        val displayPreferences by container.userPreferencesRepository
            .libraryViewPreferences
            .collectAsState(initial = LibraryViewPreferences())
        ItemDetailScreen(
            state = state,
            showQuoteChapter = displayPreferences.showQuoteChapter,
            showQuotePage = displayPreferences.showQuotePage,
            showTotalDuration = displayPreferences.libraryShowTotalDuration,
            destinationEnterCompleted = destinationEnterCompleted,
            onBack = { navController.popBackStack() },
            onEdit = { navController.navigate(ItemRoutes.edit(it)) },
            onDelete = viewModel::deleteItem,
            onDeleted = { navController.popBackStack() }
        )
    }
}

private fun androidx.navigation.NavGraphBuilder.itemEditRoute(
    navController: NavHostController,
    container: AppContainer
) {
    composable(
        route = ItemRoutes.EDIT,
        arguments = listOf(navArgument(ItemRoutes.ITEM_ID) { type = NavType.LongType })
    ) { entry ->
        val itemId = requireNotNull(entry.arguments?.getLong(ItemRoutes.ITEM_ID))
        val destinationEnterCompleted =
            rememberDestinationEnterCompleted(entry)
        val viewModel: ItemEditorViewModel = viewModel(
            factory = ItemEditorViewModelFactory(
                container.libraryUseCases,
                container.fieldUseCases,
                container.tagUseCases,
                container.coverImageUseCases,
                container.quoteUseCases,
                itemId
            )
        )
        val state by viewModel.uiState.collectAsState()
        val displayPreferences by container.userPreferencesRepository
            .libraryViewPreferences
            .collectAsState(initial = LibraryViewPreferences())
        EditItemScreen(
            state = state,
            destinationEnterCompleted = destinationEnterCompleted,
            showQuoteChapter = displayPreferences.showQuoteChapter,
            showQuotePage = displayPreferences.showQuotePage,
            onBack = {
                viewModel.discardChanges { navController.popBackStack() }
            },
            onSaved = { navController.popBackStack() },
            onTitleChange = viewModel::onTitleChange,
            onCreatorChange = viewModel::onCreatorChange,
            onCoverSelected = viewModel::selectCover,
            onRemoveCover = viewModel::removeCover,
            onStatusSelected = viewModel::onStatusSelected,
            onTagSelectionChanged = viewModel::onTagSelectionChange,
            onCreateTag = viewModel::createTag,
            onDynamicValueChange = viewModel::onDynamicValueChange,
            onRecordDraftCompleted = viewModel::onRecordDraftCompleted,
            onRecordDraftDeleted = viewModel::onRecordDraftDeleted,
            onCreateQuoteDraft = viewModel::createQuoteDraft,
            onQuoteDraftCompleted = viewModel::onQuoteDraftCompleted,
            onQuoteDraftDeleted = viewModel::onQuoteDraftDeleted,
            onSave = viewModel::save
        )
    }
}

private fun androidx.navigation.NavGraphBuilder.quoteListRoute(
    navController: NavHostController,
    container: AppContainer
) {
    composable(QuoteRoutes.LIST) {
        val viewModel: QuoteListViewModel = viewModel(
            factory = QuoteListViewModelFactory(container.quoteUseCases)
        )
        val state by viewModel.uiState.collectAsState()
        val displayPreferences by container.userPreferencesRepository
            .libraryViewPreferences
            .collectAsState(initial = LibraryViewPreferences())
        QuoteListScreen(
            state = state,
            showQuoteChapter = displayPreferences.showQuoteChapter,
            showQuotePage = displayPreferences.showQuotePage,
            onQueryChange = viewModel::onQueryChange,
            onLoadMore = viewModel::loadMore,
            onQuoteSelected = {
                navController.navigate(ItemRoutes.detail(it))
            },
            onBack = { navController.popBackStack() }
        )
    }
}

private fun androidx.navigation.NavGraphBuilder.itemTagRoute(
    navController: NavHostController,
    container: AppContainer
) {
    composable(
        route = ItemRoutes.TAGS,
        arguments = listOf(navArgument(ItemRoutes.ITEM_ID) { type = NavType.LongType })
    ) { entry ->
        val itemId = requireNotNull(entry.arguments?.getLong(ItemRoutes.ITEM_ID))
        val viewModel: ItemTagEditorViewModel = viewModel(
            factory = ItemTagEditorViewModelFactory(container.tagUseCases, itemId)
        )
        val state by viewModel.uiState.collectAsState()
        ItemTagEditorScreen(
            state = state,
            onBack = { navController.popBackStack() },
            onSelected = viewModel::setSelected
        )
    }
}

private fun androidx.navigation.NavGraphBuilder.layoutSettingsRoute(
    navController: NavHostController,
    container: AppContainer
) {
    composable(SettingsRoutes.LAYOUT) {
        val viewModel: LayoutSettingsViewModel = viewModel(
            factory = LayoutSettingsViewModelFactory(
                container.userPreferencesRepository,
                container.fieldUseCases
            )
        )
        val state by viewModel.uiState.collectAsState()
        LayoutSettingsScreen(
            state = state,
            onBack = { navController.popBackStack() },
            onTimelineCreatorChanged = viewModel::setTimelineShowCreator,
            onTimelineRatingChanged = viewModel::setTimelineShowRating,
            onTimelineStatusChanged = viewModel::setTimelineShowStatus,
            onTimelineDurationChanged = viewModel::setTimelineShowDuration,
            onLibraryTotalDurationChanged = viewModel::setLibraryShowTotalDuration,
            onQuoteChapterChanged = viewModel::setShowQuoteChapter,
            onQuotePageChanged = viewModel::setShowQuotePage,
            onGridColumnsChanged = viewModel::setGridColumns,
            onCoverColumnsChanged = viewModel::setCoverColumns,
            onListStatusChanged = viewModel::setListStatusVisible,
            onListTagsChanged = viewModel::setListTagsVisible,
            onListFieldsChanged = viewModel::setListDisplayFields
        )
    }
}

private fun androidx.navigation.NavGraphBuilder.fieldManagementRoute(
    navController: NavHostController,
    container: AppContainer
) {
    composable(SettingsRoutes.FIELDS) {
        val viewModel: FieldManagementViewModel = viewModel(
            factory = FieldManagementViewModelFactory(
                container.fieldUseCases,
                container.libraryUseCases
            )
        )
        val state by viewModel.uiState.collectAsState()
        FieldManagementScreen(
            state = state,
            onBack = { navController.popBackStack() },
            onTypeSelected = viewModel::selectType,
            onCreate = viewModel::createField,
            onUpdate = viewModel::updateField,
            onDelete = viewModel::deleteField,
            onReorder = viewModel::reorderFields,
            onAddOption = viewModel::addOption,
            onRenameOption = viewModel::renameOption,
            onDeleteOption = viewModel::deleteOption,
            onReorderOptions = viewModel::reorderOptions
        )
    }
}

private fun androidx.navigation.NavGraphBuilder.tagManagementRoute(
    navController: NavHostController,
    container: AppContainer
) {
    composable(SettingsRoutes.TAGS) {
        val viewModel: TagManagementViewModel = viewModel(
            factory = TagManagementViewModelFactory(container.tagUseCases)
        )
        val state by viewModel.uiState.collectAsState()
        TagManagementScreen(
            state = state,
            onBack = { navController.popBackStack() },
            onSelectRoot = viewModel::selectRoot,
            onCreateRoot = viewModel::createRoot,
            onCreateChildren = viewModel::createChildren,
            onRename = viewModel::rename,
            onDelete = viewModel::delete,
            onReorderRoots = viewModel::reorderRoots,
            onReorderChildren = viewModel::reorderChildren
        )
    }
}

private fun androidx.navigation.NavGraphBuilder.statusManagementRoute(
    navController: NavHostController,
    container: AppContainer
) {
    composable(SettingsRoutes.STATUSES) {
        val viewModel: StatusManagementViewModel = viewModel(
            factory = StatusManagementViewModelFactory(container.statusUseCases)
        )
        val state by viewModel.uiState.collectAsState()
        StatusManagementScreen(
            state = state,
            onBack = { navController.popBackStack() },
            onSelectScope = viewModel::selectScope,
            onCreate = viewModel::create,
            onRename = viewModel::rename,
            onDelete = viewModel::delete,
            onReorder = viewModel::reorder
        )
    }
}

private fun androidx.navigation.NavGraphBuilder.itemTypeManagementRoute(
    navController: NavHostController,
    container: AppContainer
) {
    composable(SettingsRoutes.ITEM_TYPES) {
        val viewModel: ItemTypeManagementViewModel = viewModel(
            factory = ItemTypeManagementViewModelFactory(container.itemTypeUseCases)
        )
        val state by viewModel.uiState.collectAsState()
        ItemTypeManagementScreen(
            state = state,
            onBack = { navController.popBackStack() },
            onCreate = viewModel::create,
            onRename = viewModel::rename,
            onDelete = viewModel::delete,
            onReorder = viewModel::reorder
        )
    }
}
