package com.example.mylibrary.ui.record

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mylibrary.domain.model.LibraryStatus
import com.example.mylibrary.ui.components.AppCapsule

@Composable
fun RecordStatusRow(
    statuses: List<LibraryStatus>,
    selectedName: String?,
    onSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AppCapsule(
            text = "无",
            selected = selectedName.isNullOrBlank(),
            onClick = { onSelected(null) }
        )
        selectedName?.takeIf { selected ->
            selected.isNotBlank() && statuses.none { it.name == selected }
        }?.let { historical ->
            AppCapsule(
                text = "$historical（历史值）",
                selected = true,
                onClick = {}
            )
        }
        statuses.forEach { status ->
            AppCapsule(
                text = status.name,
                selected = selectedName == status.name,
                onClick = { onSelected(status.name) }
            )
        }
    }
}
