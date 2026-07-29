package com.example.mylibrary.ui.item

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mylibrary.domain.model.LibraryRecord
import com.example.mylibrary.ui.components.StarRatingBar
import com.example.mylibrary.ui.theme.AppTheme
import com.example.mylibrary.util.parseDateText
import com.example.mylibrary.util.toLocalDate
import com.example.mylibrary.util.formatDuration
import com.example.mylibrary.util.toTotalMinutes
import java.time.format.DateTimeFormatter

private val recordDateFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")

@Composable
fun RecordContentBlock(
    record: LibraryRecord,
    modifier: Modifier = Modifier
) {
    RecordContent(
        dateText = recordDateText(record.startDate, record.endDate),
        ratingHalfStars = record.ratingHalfStars,
        review = record.review,
        durationMinutes = record.durationMinutes,
        dynamicFields = record.dynamicFields,
        modifier = modifier
    )
}

@Composable
fun RecordDraftContentBlock(
    draft: RecordDraftUiState,
    modifier: Modifier = Modifier
) {
    val start = parseDateText(draft.startDate)
    val end = parseDateText(draft.endDate)
    RecordContent(
        dateText = if (start == null) {
            draft.startDate
        } else {
            recordDateText(start, end)
        },
        ratingHalfStars = draft.ratingHalfStars,
        review = draft.review,
        durationMinutes = toTotalMinutes(
            draft.durationHoursText,
            draft.durationMinutesText
        ),
        dynamicFields = emptyList(),
        modifier = modifier
    )
}

@Composable
private fun RecordContent(
    dateText: String,
    ratingHalfStars: Int?,
    review: String?,
    durationMinutes: Long?,
    dynamicFields: List<com.example.mylibrary.domain.model.DynamicFieldValue>,
    modifier: Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = dateText,
            style = AppTheme.typography.itemTitle,
            color = AppTheme.colors.textPrimary
        )
        ratingHalfStars?.let {
            StarRatingBar(
                ratingHalfStars = it,
                starSize = 18.dp
            )
        }
        durationMinutes?.let(::formatDuration)?.let { duration ->
            Text(
                text = duration,
                modifier = Modifier,
                style = AppTheme.typography.metadata,
                color = AppTheme.colors.textSecondary
            )
        }
        review?.takeIf(String::isNotBlank)?.let {
            Text(
                text = it,
                style = AppTheme.typography.body,
                color = AppTheme.colors.textPrimary
            )
        }
        if (dynamicFields.isNotEmpty()) {
            DynamicFieldList(fields = dynamicFields)
        }
    }
}

private fun recordDateText(startDate: Long, endDate: Long?): String {
    val start = startDate.toLocalDate().format(recordDateFormatter)
    return when {
        endDate == null -> "开始于 $start"
        endDate == startDate -> start
        else -> "$start — ${endDate.toLocalDate().format(recordDateFormatter)}"
    }
}
