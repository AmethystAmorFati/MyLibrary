package com.example.mylibrary.ui.item

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mylibrary.domain.model.DynamicFieldValue
import com.example.mylibrary.domain.model.FieldDataType
import com.example.mylibrary.domain.model.decodeFieldSelection
import com.example.mylibrary.domain.model.FieldNumberFormatter
import java.math.BigDecimal
import com.example.mylibrary.ui.theme.AppTheme

@Composable
fun DynamicFieldList(
    fields: List<DynamicFieldValue>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        fields.forEach { field ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = field.name.displayFieldName(),
                    modifier = Modifier.weight(0.3f),
                    color = AppTheme.colors.mutedText,
                    style = AppTheme.typography.metadata
                )
                Text(
                    text = field.displayValue(),
                    modifier = Modifier.weight(0.7f),
                    style = AppTheme.typography.body,
                    color = AppTheme.colors.textPrimary
                )
            }
        }
    }
}

private fun DynamicFieldValue.displayValue(): String = when {
    value.isBlank() -> "未填写"
    dataType == FieldDataType.BOOLEAN -> if (value == "true") "是" else "否"
    dataType == FieldDataType.MULTI_SELECT ->
        decodeFieldSelection(value).joinToString("  ")
    dataType == FieldDataType.NUMBER && !unit.isNullOrBlank() -> "$value $unit"
    dataType == FieldDataType.RATING -> runCatching {
        FieldNumberFormatter.format(
            BigDecimal(value).divide(BigDecimal(2))
        ) + " 星"
    }.getOrDefault(value)
    else -> value
}

private fun String.displayFieldName(): String = when (this) {
    "author" -> "作者"
    "director" -> "导演"
    else -> this
}
