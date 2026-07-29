package com.example.mylibrary.di

import android.content.Context
import com.example.mylibrary.backup.BackupDatabaseStore
import com.example.mylibrary.backup.BackupRepository
import com.example.mylibrary.backup.DataExportService
import com.example.mylibrary.backup.DataImportService
import com.example.mylibrary.data.database.LibraryDatabase
import com.example.mylibrary.data.repository.OfflineLibraryRepository
import com.example.mylibrary.data.repository.OfflineCustomFieldStatisticsRepository
import com.example.mylibrary.data.repository.OfflineItemTypeRepository
import com.example.mylibrary.data.repository.OfflineQuoteRepository
import com.example.mylibrary.data.repository.OfflineFieldRepository
import com.example.mylibrary.data.repository.OfflineStatusRepository
import com.example.mylibrary.data.repository.OfflineTagRepository
import com.example.mylibrary.data.repository.OfflineTrashRepository
import com.example.mylibrary.data.repository.RoomReportDataSource
import com.example.mylibrary.data.repository.LocalCoverImageRepository
import com.example.mylibrary.data.repository.UserPreferencesRepository
import com.example.mylibrary.domain.usecase.AddRecordUseCase
import com.example.mylibrary.domain.usecase.CreateItemUseCase
import com.example.mylibrary.domain.usecase.CoverImageUseCases
import com.example.mylibrary.domain.usecase.DeleteCoverImageUseCase
import com.example.mylibrary.domain.usecase.DeleteItemUseCase
import com.example.mylibrary.domain.usecase.CreateFieldUseCase
import com.example.mylibrary.domain.usecase.UpdateFieldUseCase
import com.example.mylibrary.domain.usecase.AddFieldOptionUseCase
import com.example.mylibrary.domain.usecase.CreateStatusUseCase
import com.example.mylibrary.domain.usecase.CreateChildTagsUseCase
import com.example.mylibrary.domain.usecase.CreateTagUseCase
import com.example.mylibrary.domain.usecase.DeleteTagUseCase
import com.example.mylibrary.domain.usecase.DeleteFieldOptionUseCase
import com.example.mylibrary.domain.usecase.DeleteFieldUseCase
import com.example.mylibrary.domain.usecase.FieldUseCases
import com.example.mylibrary.domain.usecase.LibraryUseCases
import com.example.mylibrary.domain.usecase.ItemTypeUseCases
import com.example.mylibrary.domain.usecase.ObserveManagedItemTypesUseCase
import com.example.mylibrary.domain.usecase.CreateItemTypeUseCase
import com.example.mylibrary.domain.usecase.RenameItemTypeUseCase
import com.example.mylibrary.domain.usecase.DeleteItemTypeUseCase
import com.example.mylibrary.domain.usecase.ReorderItemTypesUseCase
import com.example.mylibrary.domain.usecase.QuoteUseCases
import com.example.mylibrary.domain.usecase.ObserveItemDetailUseCase
import com.example.mylibrary.domain.usecase.ObserveItemTypesUseCase
import com.example.mylibrary.domain.usecase.ObserveFieldsUseCase
import com.example.mylibrary.domain.usecase.ObserveLibraryItemsUseCase
import com.example.mylibrary.domain.usecase.ObserveManagedStatusesUseCase
import com.example.mylibrary.domain.usecase.ObserveStatusesUseCase
import com.example.mylibrary.domain.usecase.ObserveTagsUseCase
import com.example.mylibrary.domain.usecase.MoveFieldUseCase
import com.example.mylibrary.domain.usecase.RenameFieldOptionUseCase
import com.example.mylibrary.domain.usecase.RenameFieldUseCase
import com.example.mylibrary.domain.usecase.RenameStatusUseCase
import com.example.mylibrary.domain.usecase.RenameTagUseCase
import com.example.mylibrary.domain.usecase.ReorderTagsUseCase
import com.example.mylibrary.domain.usecase.ReorderFieldOptionsUseCase
import com.example.mylibrary.domain.usecase.ReorderFieldsUseCase
import com.example.mylibrary.domain.usecase.SetFieldEnabledUseCase
import com.example.mylibrary.domain.usecase.SetItemTagUseCase
import com.example.mylibrary.domain.usecase.SetStatusEnabledUseCase
import com.example.mylibrary.domain.usecase.DeleteStatusUseCase
import com.example.mylibrary.domain.usecase.ReorderStatusesUseCase
import com.example.mylibrary.domain.usecase.SaveCoverImageUseCase
import com.example.mylibrary.domain.usecase.StatusUseCases
import com.example.mylibrary.domain.usecase.TagUseCases
import com.example.mylibrary.domain.usecase.TrashUseCases
import com.example.mylibrary.domain.usecase.ObserveTrashItemsUseCase
import com.example.mylibrary.domain.usecase.RestoreTrashItemUseCase
import com.example.mylibrary.domain.usecase.PermanentlyDeleteTrashItemUseCase
import com.example.mylibrary.domain.usecase.EmptyTrashUseCase
import com.example.mylibrary.domain.usecase.UpdateItemUseCase
import com.example.mylibrary.domain.usecase.UpdateItemStatusUseCase
import com.example.mylibrary.domain.usecase.UpdateRecordUseCase
import com.example.mylibrary.domain.usecase.DeleteRecordUseCase
import com.example.mylibrary.domain.usecase.ObserveActivitiesUseCase
import com.example.mylibrary.domain.usecase.ObserveTimelineRecordsUseCase
import com.example.mylibrary.domain.usecase.ObserveCustomFieldStatisticsUseCase
import com.example.mylibrary.domain.usecase.SaveItemUseCase
import com.example.mylibrary.ui.theme.DefaultThemeRepository
import com.example.mylibrary.ui.theme.ThemeRepository
import com.example.mylibrary.export.report.ReportDataResolver

interface AppContainer {
    val database: LibraryDatabase
    val libraryUseCases: LibraryUseCases
    val quoteUseCases: QuoteUseCases
    val observeCustomFieldStatistics: ObserveCustomFieldStatisticsUseCase
    val fieldUseCases: FieldUseCases
    val tagUseCases: TagUseCases
    val statusUseCases: StatusUseCases
    val itemTypeUseCases: ItemTypeUseCases
    val coverImageUseCases: CoverImageUseCases
    val trashUseCases: TrashUseCases
    val userPreferencesRepository: UserPreferencesRepository
    val backupRepository: BackupRepository
    val reportDataResolver: ReportDataResolver
    val themeRepository: ThemeRepository
}

class DefaultAppContainer(
    context: Context
) : AppContainer {
    override val themeRepository: ThemeRepository = DefaultThemeRepository()

    override val database: LibraryDatabase =
        LibraryDatabase.getInstance(context)

    private val libraryRepository = OfflineLibraryRepository(
        database = database,
        itemDao = database.itemDao(),
        itemTypeDao = database.itemTypeDao(),
        statusDao = database.statusDao(),
        recordDao = database.recordDao(),
        activityDao = database.activityDao(),
        dynamicFieldDao = database.dynamicFieldDao(),
        tagDao = database.tagDao()
    )
    private val fieldRepository =
        OfflineFieldRepository(database, database.dynamicFieldDao())
    private val tagRepository =
        OfflineTagRepository(database, database.itemDao(), database.tagDao())
    private val statusRepository =
        OfflineStatusRepository(database, database.statusDao())
    private val itemTypeRepository =
        OfflineItemTypeRepository(
            database,
            database.itemTypeDao(),
            database.dynamicFieldDao()
        )
    private val coverImageRepository = LocalCoverImageRepository(context)
    private val trashRepository = OfflineTrashRepository(
        database = database,
        itemDao = database.itemDao(),
        coverImageRepository = coverImageRepository
    )
    override val userPreferencesRepository =
        UserPreferencesRepository(context.applicationContext)
    private val backupDatabaseStore = BackupDatabaseStore(database)
    private val quoteRepository = OfflineQuoteRepository(
        quoteDao = database.quoteDao(),
        itemDao = database.itemDao()
    )
    private val customFieldStatisticsRepository =
        OfflineCustomFieldStatisticsRepository(database.dynamicFieldDao())
    override val reportDataResolver =
        ReportDataResolver(RoomReportDataSource(database))

    override val libraryUseCases = LibraryUseCases(
        observeItems = ObserveLibraryItemsUseCase(libraryRepository),
        observeTypes = ObserveItemTypesUseCase(libraryRepository),
        observeStatuses = ObserveStatusesUseCase(libraryRepository),
        observeItemDetail = ObserveItemDetailUseCase(libraryRepository),
        observeActivities = ObserveActivitiesUseCase(libraryRepository),
        observeTimelineRecords = ObserveTimelineRecordsUseCase(libraryRepository),
            createItem = CreateItemUseCase(libraryRepository),
            updateItem = UpdateItemUseCase(libraryRepository),
            saveItem = SaveItemUseCase(libraryRepository),
        updateItemStatus = UpdateItemStatusUseCase(libraryRepository),
        deleteItem = DeleteItemUseCase(libraryRepository),
        addRecord = AddRecordUseCase(libraryRepository),
        updateRecord = UpdateRecordUseCase(libraryRepository),
        deleteRecord = DeleteRecordUseCase(libraryRepository)
    )

    override val fieldUseCases = FieldUseCases(
        observe = ObserveFieldsUseCase(fieldRepository),
        create = CreateFieldUseCase(fieldRepository),
        update = UpdateFieldUseCase(fieldRepository),
        rename = RenameFieldUseCase(fieldRepository),
        delete = DeleteFieldUseCase(fieldRepository),
        reorder = ReorderFieldsUseCase(fieldRepository),
        addOption = AddFieldOptionUseCase(fieldRepository),
        renameOption = RenameFieldOptionUseCase(fieldRepository),
        deleteOption = DeleteFieldOptionUseCase(fieldRepository),
        reorderOptions = ReorderFieldOptionsUseCase(fieldRepository),
        setEnabled = SetFieldEnabledUseCase(fieldRepository),
        move = MoveFieldUseCase(fieldRepository)
    )

    override val quoteUseCases = QuoteUseCases(quoteRepository)
    override val observeCustomFieldStatistics =
        ObserveCustomFieldStatisticsUseCase(customFieldStatisticsRepository)

    override val tagUseCases = TagUseCases(
        observe = ObserveTagsUseCase(tagRepository),
        create = CreateTagUseCase(tagRepository),
        createChildren = CreateChildTagsUseCase(tagRepository),
        rename = RenameTagUseCase(tagRepository),
        delete = DeleteTagUseCase(tagRepository),
        reorder = ReorderTagsUseCase(tagRepository),
        setItemTag = SetItemTagUseCase(tagRepository)
    )

    override val statusUseCases = StatusUseCases(
        observe = ObserveManagedStatusesUseCase(statusRepository),
        create = CreateStatusUseCase(statusRepository),
        rename = RenameStatusUseCase(statusRepository),
        setEnabled = SetStatusEnabledUseCase(statusRepository),
        delete = DeleteStatusUseCase(statusRepository),
        reorder = ReorderStatusesUseCase(statusRepository)
    )

    override val itemTypeUseCases = ItemTypeUseCases(
        observe = ObserveManagedItemTypesUseCase(itemTypeRepository),
        create = CreateItemTypeUseCase(itemTypeRepository),
        rename = RenameItemTypeUseCase(itemTypeRepository),
        delete = DeleteItemTypeUseCase(itemTypeRepository),
        reorder = ReorderItemTypesUseCase(itemTypeRepository)
    )

    override val coverImageUseCases = CoverImageUseCases(
        save = SaveCoverImageUseCase(coverImageRepository),
        delete = DeleteCoverImageUseCase(coverImageRepository)
    )

    override val trashUseCases = TrashUseCases(
        observe = ObserveTrashItemsUseCase(trashRepository),
        restore = RestoreTrashItemUseCase(trashRepository),
        permanentlyDelete = PermanentlyDeleteTrashItemUseCase(trashRepository),
        empty = EmptyTrashUseCase(trashRepository)
    )

    override val backupRepository = BackupRepository(
        exportService = DataExportService(
            context = context,
            databaseStore = backupDatabaseStore,
            preferencesRepository = userPreferencesRepository,
            coverImageRepository = coverImageRepository
        ),
        importService = DataImportService(
            context = context,
            databaseStore = backupDatabaseStore,
            preferencesRepository = userPreferencesRepository,
            coverImageRepository = coverImageRepository
        )
    )
}
