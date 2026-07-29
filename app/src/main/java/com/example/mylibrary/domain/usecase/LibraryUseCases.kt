package com.example.mylibrary.domain.usecase

data class LibraryUseCases(
    val observeItems: ObserveLibraryItemsUseCase,
    val observeTypes: ObserveItemTypesUseCase,
    val observeStatuses: ObserveStatusesUseCase,
    val observeItemDetail: ObserveItemDetailUseCase,
    val observeActivities: ObserveActivitiesUseCase,
    val observeTimelineRecords: ObserveTimelineRecordsUseCase,
    val createItem: CreateItemUseCase,
    val updateItem: UpdateItemUseCase,
    val saveItem: SaveItemUseCase,
    val updateItemStatus: UpdateItemStatusUseCase,
    val deleteItem: DeleteItemUseCase,
    val addRecord: AddRecordUseCase,
    val updateRecord: UpdateRecordUseCase,
    val deleteRecord: DeleteRecordUseCase
)
