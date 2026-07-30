package com.example.mylibrary.export.report

import android.content.Context
import android.util.Log
import com.example.mylibrary.data.image.CoverInputValidator
import com.example.mylibrary.data.image.resolveStoredCoverFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun resolveReportCovers(
    context: Context,
    snapshot: ReportDataSnapshot,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO
): ReportDataSnapshot = withContext(ioDispatcher) {
    val appContext = context.applicationContext
    val metadata = mutableMapOf<String, com.example.mylibrary.domain.model.CoverImageMetadata?>()
    fun valid(path: String?): Pair<String, com.example.mylibrary.domain.model.CoverImageMetadata>? {
        val value = path?.takeIf(String::isNotBlank) ?: return null
        val resolved = metadata.getOrPut(value) {
            try {
                val file = resolveStoredCoverFile(appContext, value)
                file?.let(CoverInputValidator::validate)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "Skipping invalid report cover: $value", error)
                null
            }
        }
        return resolved?.let { value to it }
    }

    val items = snapshot.items.map { item ->
        val cover = valid(item.coverPath)
        item.copy(
            coverPath = cover?.first,
            resolvedCoverWidth = cover?.second?.width,
            resolvedCoverHeight = cover?.second?.height
        )
    }
    val byId = items.associateBy(ReportItemSnapshot::itemId)
    val representative = items
        .sortedWith(
            compareByDescending<ReportItemSnapshot> { it.activityDayCount }
                .thenBy { it.firstActivityDate }
                .thenBy { it.itemId }
        )
        .firstOrNull { it.coverPath != null }
        ?.itemId
    snapshot.copy(
        items = items,
        representativeItemId = representative,
        monthlySummaries = snapshot.monthlySummaries.map { month ->
            month.copy(
                representativeItemId = month.representativeCandidateItemIds
                    .firstOrNull { byId[it]?.coverPath != null }
            )
        }
    )
}

private const val TAG = "ReportCovers"
