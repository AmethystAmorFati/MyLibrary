package com.example.mylibrary.backup

import com.example.mylibrary.backup.model.BackupActivity
import com.example.mylibrary.backup.model.BackupData
import com.example.mylibrary.backup.model.BackupFieldDefinition
import com.example.mylibrary.backup.model.BackupFieldOption
import com.example.mylibrary.backup.model.BackupFieldValue
import com.example.mylibrary.backup.model.BackupItem
import com.example.mylibrary.backup.model.BackupItemTag
import com.example.mylibrary.backup.model.BackupItemType
import com.example.mylibrary.backup.model.BackupQuote
import com.example.mylibrary.backup.model.BackupRecord
import com.example.mylibrary.backup.model.BackupRecordFieldValue
import com.example.mylibrary.backup.model.BackupPreferences
import com.example.mylibrary.backup.model.BackupStatus
import com.example.mylibrary.backup.model.BackupTag
import com.example.mylibrary.backup.serialization.BackupJsonCodec
import com.example.mylibrary.backup.serialization.BackupMigrationChain
import com.example.mylibrary.backup.validation.BackupDataValidator
import com.example.mylibrary.domain.model.encodeFieldSelection
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupJsonCodecTest {
    private val codec = BackupJsonCodec()

    @Test
    fun fullDataRoundTripPreservesTextNullsAndRelationships() {
        val source = richBackupData()

        val decoded = codec.decodeData(codec.parseDataObject(codec.encodeData(source)))

        assertEquals(source, decoded)
        BackupDataValidator().validate(
            data = decoded,
            availableCoverPaths = setOf(
                "covers/original/cover_000001.png",
                "covers/original/cover_000002.webp"
            )
        )
    }

    @Test
    fun currentBackupRoundTripPreservesFieldCapabilitiesAndBothValueScopes() {
        val source = richBackupData()
        val decoded = codec.decodeData(codec.parseDataObject(codec.encodeData(source)))
        val itemField = decoded.fieldDefinitions.single { it.id == 3L }
        val recordField = decoded.fieldDefinitions.single { it.id == 7L }

        assertEquals("item", itemField.scope)
        assertEquals("字", itemField.unit)
        assertEquals(setOf("sum", "average"), itemField.aggregations)
        assertEquals("record", recordField.scope)
        assertEquals("次", recordField.unit)
        assertEquals(setOf("sum", "average"), recordField.aggregations)
        assertEquals(source.fieldValues, decoded.fieldValues)
        assertEquals(source.recordFieldValues, decoded.recordFieldValues)
    }

    @Test
    fun preferencesRoundTripPreservesPersistentDisplaySettings() {
        val source = BackupPreferences(
            useGridLayout = false,
            libraryViewMode = "list",
            gridColumns = 3,
            coverColumns = 5,
            timelineShowCreator = true,
            timelineShowRating = true,
            timelineShowStatus = true,
            timelineShowDuration = false,
            libraryShowTotalDuration = false,
            showQuoteChapter = false,
            showQuotePage = true,
            listDisplayFields = setOf("creator", "current_status", "dynamic:42")
        )

        assertEquals(source, codec.decodePreferences(codec.encodePreferences(source)))
    }

    @Test
    fun legacyPreferencesDefaultNewQuoteAndDurationVisibilityToEnabled() {
        val decoded = codec.decodePreferences(
            """
            {
              "useGridLayout": false,
              "libraryViewMode": "list",
              "gridColumns": 4,
              "coverColumns": 4,
              "timelineShowCreator": false,
              "timelineShowRating": false,
              "timelineShowStatus": true,
              "listDisplayFields": ["creator"]
            }
            """.trimIndent()
        )

        assertEquals(true, decoded.timelineShowDuration)
        assertEquals(true, decoded.libraryShowTotalDuration)
        assertEquals(true, decoded.showQuoteChapter)
        assertEquals(true, decoded.showQuotePage)
    }

    @Test
    fun versionThreeBackupGetsRoundFourDefaults() {
        val legacy = codec.parseDataObject(
            """
            {
              "itemTypes": [],
              "statuses": [{
                "id": 1, "name": "完成", "sortOrder": 0, "enabled": true
              }],
              "fieldDefinitions": [],
              "tags": [],
              "items": [],
              "records": [{
                "id": 1, "itemId": 9, "startDate": 1, "endDate": null,
                "ratingHalfStars": null, "review": null, "createdAt": 2
              }],
              "activities": [],
              "itemTags": [],
              "fieldValues": [],
              "quotes": [{
                "id": 1, "itemId": 9, "content": "摘录", "source": null,
                "page": null, "createdTime": 3
              }],
              "recordFieldValues": []
            }
            """.trimIndent()
        )

        val decoded = codec.decodeData(
            BackupMigrationChain().migrate(legacy, sourceVersion = 3)
        )

        assertEquals("item", decoded.statuses.single().scope)
        assertEquals(null, decoded.records.single().statusSnapshot)
        assertEquals(null, decoded.records.single().durationMinutes)
        assertEquals(null, decoded.quotes.single().chapter)
    }

    @Test
    fun versionOneBackupOptionsMigrateToActiveStableDefinitions() {
        val legacy = codec.parseDataObject(
            """
            {
              "itemTypes": [],
              "statuses": [],
              "fieldDefinitions": [{
                "id": 7,
                "typeId": 1,
                "name": "阅读方式",
                "dataType": "single_select",
                "enabled": true,
                "sortOrder": 0,
                "isFixed": false,
                "options": ["纸质书", "电子书"]
              }],
              "tags": [],
              "items": [],
              "records": [],
              "activities": [],
              "itemTags": [],
              "fieldValues": [],
              "quotes": []
            }
            """.trimIndent()
        )

        val migrated = BackupMigrationChain().migrate(legacy, sourceVersion = 1)
        val field = codec.decodeData(migrated).fieldDefinitions.single()

        assertEquals(listOf(1L, 2L), field.optionDefinitions.map { it.id })
        assertEquals(listOf("纸质书", "电子书"), field.options)
        assertEquals(true, field.optionDefinitions.all { it.isActive })
        assertEquals("item", field.scope)
        assertEquals(null, field.unit)
        assertEquals(emptySet<String>(), field.aggregations)
        assertEquals(
            emptyList<BackupRecordFieldValue>(),
            codec.decodeData(migrated).recordFieldValues
        )
    }

    @Test
    fun largeLogicalBackupRoundTripDoesNotLoadImages() {
        val items = (1L..3_000L).map { id ->
            BackupItem(
                id = id,
                typeId = 1,
                title = "作品 $id 📚",
                coverRef = null,
                currentStatusId = null,
                createdTime = id,
                updatedTime = id,
                deletedAt = null
            )
        }
        val quotes = (1L..20_000L).map { id ->
            BackupQuote(
                id = id,
                itemId = (id % 3_000L) + 1L,
                content = "摘录 $id\n第二行",
                source = null,
                page = if (id % 2L == 0L) id.toString() else null,
                createdTime = id
            )
        }
        val source = BackupData(
            itemTypes = listOf(BackupItemType(1, "Book", 0)),
            statuses = emptyList(),
            fieldDefinitions = emptyList(),
            tags = emptyList(),
            items = items,
            records = emptyList(),
            activities = emptyList(),
            itemTags = emptyList(),
            fieldValues = emptyList(),
            quotes = quotes
        )

        val decoded = codec.decodeData(codec.parseDataObject(codec.encodeData(source)))

        assertEquals(3_000, decoded.items.size)
        assertEquals(20_000, decoded.quotes.size)
        BackupDataValidator().validate(decoded, emptySet())
    }

    private fun richBackupData(): BackupData = BackupData(
        itemTypes = listOf(
            BackupItemType(1, "Book", 0),
            BackupItemType(2, "Movie", 1),
            BackupItemType(3, "播客 🎧", 2)
        ),
        statuses = listOf(
            BackupStatus(1, "想看", 0, true),
            BackupStatus(2, "进行中", 1, true),
            BackupStatus(3, "进行中", 0, true, scope = "record")
        ),
        fieldDefinitions = listOf(
            BackupFieldDefinition(1, 1, "author", "text", true, 0, true, emptyList()),
            BackupFieldDefinition(2, 2, "director", "text", true, 0, true, emptyList()),
            BackupFieldDefinition(
                id = 3,
                typeId = 1,
                name = "字数",
                dataType = "number",
                enabled = true,
                sortOrder = 1,
                isFixed = false,
                options = emptyList(),
                scope = "item",
                unit = "字",
                aggregations = setOf("sum", "average")
            ),
            BackupFieldDefinition(4, 1, "日期", "date", true, 2, false, emptyList()),
            BackupFieldDefinition(
                5,
                1,
                "版本",
                "single_select",
                true,
                3,
                false,
                options = listOf("纸质"),
                optionDefinitions = listOf(
                    BackupFieldOption(101, "纸质", true, 0),
                    BackupFieldOption(102, "电子", false, 1)
                )
            ),
            BackupFieldDefinition(
                6,
                1,
                "主题",
                "multi_select",
                true,
                4,
                false,
                listOf("历史", "科学")
            ),
            BackupFieldDefinition(
                id = 7,
                typeId = 1,
                name = "专注次数",
                dataType = "number",
                enabled = true,
                sortOrder = 5,
                isFixed = false,
                options = emptyList(),
                scope = "record",
                unit = "次",
                aggregations = setOf("sum", "average")
            )
        ),
        tags = listOf(
            BackupTag(1, "文学", null, 0, true),
            BackupTag(2, "科幻", 1, 0, true),
            BackupTag(3, "收藏", null, 1, true)
        ),
        items = listOf(
            BackupItem(
                1,
                1,
                "三体 📖",
                "covers/original/cover_000001.png",
                2,
                1_700_000_000_000,
                1_700_000_100_000,
                null
            ),
            BackupItem(
                2,
                2,
                "",
                "covers/original/cover_000002.webp",
                null,
                1_700_000_200_000,
                1_700_000_300_000,
                null
            )
        ),
        records = listOf(
            BackupRecord(
                1,
                1,
                19_000,
                19_001,
                9,
                "很好\n第二行",
                10,
                statusSnapshot = "已完成",
                durationMinutes = 90
            ),
            BackupRecord(2, 1, 19_002, null, null, "", 11)
        ),
        activities = listOf(
            BackupActivity(1, 19_000, 1, 1),
            BackupActivity(2, 19_002, 1, 2)
        ),
        itemTags = listOf(
            BackupItemTag(1, 2),
            BackupItemTag(1, 3)
        ),
        fieldValues = listOf(
            BackupFieldValue(1, 1, 1, "刘慈欣"),
            BackupFieldValue(2, 1, 3, "302000"),
            BackupFieldValue(3, 1, 4, "2026-07-26"),
            BackupFieldValue(4, 1, 5, encodeFieldSelection(listOf("电子"))),
            BackupFieldValue(5, 1, 6, encodeFieldSelection(listOf("历史", "科学")))
        ),
        quotes = listOf(
            BackupQuote(
                1,
                1,
                "给岁月以文明。\n给时光以生命。🌌",
                null,
                "42",
                12,
                chapter = "第一章"
            ),
            BackupQuote(2, 1, "", "卷二", null, 13)
        ),
        recordFieldValues = listOf(
            BackupRecordFieldValue(1, 1, 7, "12.5"),
            BackupRecordFieldValue(2, 2, 7, "9")
        )
    )
}
