package com.example.mylibrary.backup.validation

import com.example.mylibrary.backup.model.BackupData
import com.example.mylibrary.backup.model.BackupFieldDefinition
import com.example.mylibrary.backup.model.BackupItemType
import com.example.mylibrary.backup.model.BackupStatus
import com.example.mylibrary.domain.model.FieldDataType

class BackupDefaultDataNormalizer {
    fun normalize(source: BackupData): BackupData {
        val types = source.itemTypes.toMutableList()
        val usedTypeIds = types.mapTo(mutableSetOf()) { it.id }
        require(
            types.none {
                it.id != 1L && it.name.equals("Book", ignoreCase = true)
            }
        ) {
            "Backup uses the reserved Book name without canonical type ID 1"
        }
        require(
            types.none {
                it.id != 2L && it.name.equals("Movie", ignoreCase = true)
            }
        ) {
            "Backup uses the reserved Movie name without canonical type ID 2"
        }
        // Schema 12 has no immutable semantic-key column. The only stable
        // contract available to current code is the built-in IDs; a mutable
        // display name at another ID must never be promoted to BOOK/MOVIE.
        val book = types.firstOrNull { it.id == 1L }
            ?: BackupItemType(
                id = availableId(1L, usedTypeIds),
                name = "Book",
                sortOrder = nextSortOrder(types.map { it.sortOrder })
            ).also { types += it }
        val movie = types.firstOrNull { it.id == 2L }
            ?: BackupItemType(
                id = availableId(2L, usedTypeIds),
                name = "Movie",
                sortOrder = nextSortOrder(types.map { it.sortOrder })
            ).also { types += it }

        val statuses = source.statuses.toMutableList()
        val usedStatusIds = statuses.mapTo(mutableSetOf()) { it.id }
        listOf(
            1L to "想看",
            2L to "进行中",
            3L to "完成",
            4L to "暂停"
        ).forEach { (preferredId, name) ->
            if (statuses.none { it.scope == "item" && it.name == name }) {
                statuses += BackupStatus(
                    id = availableId(preferredId, usedStatusIds),
                    name = name,
                    sortOrder = nextSortOrder(
                        statuses
                            .filter { it.scope == "item" }
                            .map { it.sortOrder }
                    ),
                    enabled = true,
                    scope = "item"
                )
            }
        }

        val fields = source.fieldDefinitions.toMutableList()
        val usedFieldIds = fields.mapTo(mutableSetOf()) { it.id }
        addFixedFieldIfMissing(fields, usedFieldIds, 1L, book.id, "author")
        addFixedFieldIfMissing(fields, usedFieldIds, 2L, movie.id, "director")

        return source.copy(
            itemTypes = types.sortedWith(compareBy({ it.sortOrder }, { it.id })),
            statuses = statuses.sortedWith(
                compareBy({ it.scope }, { it.sortOrder }, { it.id })
            ),
            fieldDefinitions = fields.sortedWith(
                compareBy({ it.typeId }, { it.sortOrder }, { it.id })
            )
        )
    }

    private fun addFixedFieldIfMissing(
        fields: MutableList<BackupFieldDefinition>,
        usedIds: MutableSet<Long>,
        preferredId: Long,
        typeId: Long,
        name: String
    ) {
        if (fields.any { it.typeId == typeId && it.name.equals(name, ignoreCase = true) }) return
        fields += BackupFieldDefinition(
            id = availableId(preferredId, usedIds),
            typeId = typeId,
            name = name,
            dataType = FieldDataType.TEXT.storageValue,
            enabled = true,
            sortOrder = nextSortOrder(
                fields.filter { it.typeId == typeId }.map { it.sortOrder }
            ),
            isFixed = true,
            options = emptyList()
        )
    }

    private fun availableId(preferred: Long, used: MutableSet<Long>): Long {
        val result = if (preferred !in used) {
            preferred
        } else {
            generateSequence((used.maxOrNull() ?: 0L) + 1L) { it + 1L }
                .first { it !in used }
        }
        used += result
        return result
    }

    private fun nextSortOrder(values: List<Int>): Int = (values.maxOrNull() ?: -1) + 1
}
