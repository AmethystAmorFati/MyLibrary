package com.example.mylibrary.backup

import com.example.mylibrary.backup.model.BackupData
import com.example.mylibrary.backup.model.BackupFieldDefinition
import com.example.mylibrary.backup.model.BackupFieldValue
import com.example.mylibrary.backup.model.BackupItem
import com.example.mylibrary.backup.model.BackupItemType
import com.example.mylibrary.backup.validation.BackupDataValidator
import com.example.mylibrary.backup.validation.BackupDefaultDataNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupValidationTest {
    private val validator = BackupDataValidator()

    @Test
    fun rejectsDuplicatePrimaryKeyAndMissingForeignKey() {
        val duplicate = minimalData().copy(
            items = listOf(minimalItem(), minimalItem())
        )
        assertThrows(IllegalArgumentException::class.java) {
            validator.validate(duplicate, emptySet())
        }

        val missingType = minimalData().copy(
            items = listOf(minimalItem().copy(typeId = 99))
        )
        assertThrows(IllegalArgumentException::class.java) {
            validator.validate(missingType, emptySet())
        }
    }

    @Test
    fun rejectsMissingCoverAndInvalidSingleSelection() {
        val missingCover = minimalData().copy(
            items = listOf(
                minimalItem().copy(coverRef = "covers/original/missing.jpg")
            )
        )
        assertThrows(IllegalArgumentException::class.java) {
            validator.validate(missingCover, emptySet())
        }

        val invalidSelection = minimalData().copy(
            fieldDefinitions = listOf(
                BackupFieldDefinition(
                    1,
                    1,
                    "版本",
                    "single_select",
                    true,
                    0,
                    false,
                    listOf("A", "B")
                )
            ),
            items = listOf(minimalItem()),
            fieldValues = listOf(BackupFieldValue(1, 1, 1, "A\u001FB"))
        )
        assertThrows(IllegalArgumentException::class.java) {
            validator.validate(invalidSelection, emptySet())
        }
    }

    @Test
    fun defaultNormalizerAddsMissingDefaultsWithoutDuplicatingExistingOnes() {
        val normalized = BackupDefaultDataNormalizer().normalize(minimalData())
        val normalizedAgain = BackupDefaultDataNormalizer().normalize(normalized)

        assertEquals(normalized, normalizedAgain)
        assertEquals(1, normalized.itemTypes.count { it.name == "Book" })
        assertEquals(1, normalized.itemTypes.count { it.name == "Movie" })
        assertEquals(1, normalized.statuses.count { it.name == "想看" })
        assertEquals(
            1,
            normalized.fieldDefinitions.count {
                it.name == "author" &&
                    it.typeId == normalized.itemTypes.single { type -> type.name == "Book" }.id
            }
        )
    }

    @Test
    fun defaultNormalizerKeepsRenamedBuiltInTypesByStableId() {
        val normalized = BackupDefaultDataNormalizer().normalize(
            minimalData().copy(
                itemTypes = listOf(
                    BackupItemType(1, "图书", 1),
                    BackupItemType(2, "影片", 0)
                )
            )
        )

        assertEquals(listOf("影片", "图书"), normalized.itemTypes.map { it.name })
        assertEquals(2, normalized.itemTypes.size)
        assertEquals(
            "author",
            normalized.fieldDefinitions.single { it.typeId == 1L }.name
        )
        assertEquals(
            "director",
            normalized.fieldDefinitions.single { it.typeId == 2L }.name
        )
    }

    @Test
    fun nonCanonicalTypeUsingBuiltInNameIsRejectedInsteadOfSilentlyRemapped() {
        val failure = runCatching {
            BackupDefaultDataNormalizer().normalize(
                minimalData().copy(
                    itemTypes = listOf(
                        BackupItemType(99, "Book", 0),
                        BackupItemType(100, "Movie", 1)
                    )
                )
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message.orEmpty().contains("canonical type ID"))
    }

    private fun minimalData() = BackupData(
        itemTypes = listOf(BackupItemType(1, "Book", 0)),
        statuses = emptyList(),
        fieldDefinitions = emptyList(),
        tags = emptyList(),
        items = emptyList(),
        records = emptyList(),
        activities = emptyList(),
        itemTags = emptyList(),
        fieldValues = emptyList(),
        quotes = emptyList()
    )

    private fun minimalItem() = BackupItem(
        id = 1,
        typeId = 1,
        title = "A",
        coverRef = null,
        currentStatusId = null,
        createdTime = 1,
        updatedTime = 1,
        deletedAt = null
    )
}
