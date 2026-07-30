package com.example.mylibrary.export.visual

import com.example.mylibrary.data.dao.ActivityDao

data class VisualExportActivity(
    val activityId: Long,
    val date: Long,
    val itemId: Long,
    val typeId: Long,
    val recordId: Long?,
    val recordCreatedAt: Long,
    val title: String,
    val coverPath: String?,
    val thumbnailPath: String?
)

interface VisualExportDataSource {
    suspend fun activitiesBetween(
        startDate: Long,
        endDate: Long
    ): List<VisualExportActivity>
}

class RoomVisualExportDataSource(
    private val activityDao: ActivityDao
) : VisualExportDataSource {
    override suspend fun activitiesBetween(
        startDate: Long,
        endDate: Long
    ): List<VisualExportActivity> =
        activityDao.getVisualExportRowsBetween(startDate, endDate).map { row ->
            VisualExportActivity(
                activityId = row.activityId,
                date = row.date,
                itemId = row.itemId,
                typeId = row.typeId,
                recordId = row.recordId,
                recordCreatedAt = row.recordCreatedAt,
                title = row.title,
                coverPath = row.coverPath,
                thumbnailPath = row.thumbnailPath
            )
        }
}
