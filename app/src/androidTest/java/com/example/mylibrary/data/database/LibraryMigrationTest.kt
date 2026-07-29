package com.example.mylibrary.data.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class LibraryMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LibraryDatabase::class.java
    )

    @Test
    fun migrationOneToTwoPreservesItemsAndAddsStatuses() {
        helper.createDatabase(TEST_DATABASE, 1).apply {
            execSQL("INSERT INTO item_types (id, name, sort_order) VALUES (1, 'Book', 0)")
            execSQL(
                """
                INSERT INTO items (
                    id, type_id, title, cover_path, thumbnail_path,
                    created_time, updated_time
                ) VALUES (8, 1, 'Migrated Book', NULL, NULL, 100, 100)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO records (
                    id, item_id, start_date, end_date, rating, review
                ) VALUES (9, 8, 100, NULL, 8.0, 'kept')
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO activities (id, date, item_id, record_id)
                VALUES (10, 100, 8, 9)
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            2,
            true,
            LibraryMigrations.MIGRATION_1_2
        ).use { database ->
            database.query("SELECT title, deleted_at FROM items WHERE id = 8").use {
                assertTrue(it.moveToFirst())
                assertEquals("Migrated Book", it.getString(0))
                assertTrue(it.isNull(1))
            }
            database.query("SELECT status_id, review FROM records WHERE id = 9").use {
                assertTrue(it.moveToFirst())
                assertTrue(it.isNull(0))
                assertEquals("kept", it.getString(1))
            }
            database.query("SELECT record_id FROM activities WHERE id = 10").use {
                assertTrue(it.moveToFirst())
                assertEquals(9L, it.getLong(0))
            }
            database.query("SELECT name FROM statuses ORDER BY sort_order").use {
                val names = buildList {
                    while (it.moveToNext()) add(it.getString(0))
                }
                assertEquals(listOf("想看", "进行中", "完成", "暂停"), names)
            }
        }
    }

    @Test
    fun migrationTwoToThreeMarksFixedFieldsAndPreservesTags() {
        helper.createDatabase(TEST_DATABASE_V3, 2).apply {
            execSQL("INSERT INTO item_types (id, name, sort_order) VALUES (1, 'Book', 0)")
            execSQL("INSERT INTO item_types (id, name, sort_order) VALUES (2, 'Movie', 1)")
            execSQL(
                """
                INSERT INTO field_definitions (
                    id, type_id, name, data_type, enabled, sort_order
                ) VALUES
                    (1, 1, 'author', 'text', 1, 0),
                    (2, 2, 'director', 'text', 1, 0),
                    (3, 1, '出版社', 'text', 1, 1)
                """.trimIndent()
            )
            execSQL("INSERT INTO tags (id, name, parent_id) VALUES (4, '文学', NULL)")
            execSQL("INSERT INTO tags (id, name, parent_id) VALUES (5, '小说', 4)")
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE_V3,
            3,
            true,
            LibraryMigrations.MIGRATION_2_3
        ).use { database ->
            database.query(
                "SELECT name, is_fixed FROM field_definitions ORDER BY id"
            ).use {
                val values = buildList {
                    while (it.moveToNext()) add(it.getString(0) to it.getInt(1))
                }
                assertEquals(
                    listOf("author" to 1, "director" to 1, "出版社" to 0),
                    values
                )
            }
            database.query("SELECT name, parent_id, enabled FROM tags ORDER BY id").use {
                assertTrue(it.moveToFirst())
                assertEquals("文学", it.getString(0))
                assertTrue(it.isNull(1))
                assertEquals(1, it.getInt(2))
                assertTrue(it.moveToNext())
                assertEquals("小说", it.getString(0))
                assertEquals(4L, it.getLong(1))
                assertEquals(1, it.getInt(2))
            }
        }
    }

    @Test
    fun migrationThreeToFourCopiesLatestRecordStatusAndUsesDefault() {
        helper.createDatabase(TEST_DATABASE_V4, 3).apply {
            execSQL("INSERT INTO item_types (id, name, sort_order) VALUES (1, 'Book', 0)")
            execSQL(
                """
                INSERT INTO statuses (id, name, sort_order, enabled) VALUES
                    (1, '想看', 0, 1),
                    (2, '进行中', 1, 1),
                    (3, '完成', 2, 1),
                    (4, '暂停', 3, 1)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO items (
                    id, type_id, title, cover_path, thumbnail_path,
                    created_time, updated_time, deleted_at
                ) VALUES
                    (20, 1, 'With Records', NULL, NULL, 1, 1, NULL),
                    (21, 1, 'Without Records', NULL, NULL, 1, 1, NULL)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO records (
                    id, item_id, status_id, start_date, end_date, rating, review
                ) VALUES
                    (30, 20, 3, 100, 150, 8.0, 'first'),
                    (31, 20, 4, 200, NULL, NULL, 'latest')
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE_V4,
            4,
            true,
            LibraryMigrations.MIGRATION_3_4
        ).use { database ->
            database.query(
                "SELECT id, current_status_id FROM items ORDER BY id"
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals(20L, it.getLong(0))
                assertEquals(4L, it.getLong(1))
                assertTrue(it.moveToNext())
                assertEquals(21L, it.getLong(0))
                assertEquals(1L, it.getLong(1))
            }
            database.query(
                "SELECT id, status_id FROM records WHERE item_id = 20 ORDER BY id"
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals(3L, it.getLong(1))
                assertTrue(it.moveToNext())
                assertEquals(4L, it.getLong(1))
            }
        }
    }

    @Test
    fun migrationFourToFiveRebuildsDateRangesAndRemovesDuplicates() {
        val dayOne = dayMillis(2026, 7, 1)
        val dayTwo = dayMillis(2026, 7, 2)
        val dayThree = dayMillis(2026, 7, 3)
        helper.createDatabase(TEST_DATABASE_V5, 4).apply {
            execSQL("INSERT INTO item_types (id, name, sort_order) VALUES (1, 'Book', 0)")
            execSQL(
                """
                INSERT INTO statuses (id, name, sort_order, enabled)
                VALUES (1, '想看', 0, 1), (2, '进行中', 1, 1)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO items (
                    id, type_id, title, cover_path, thumbnail_path,
                    created_time, updated_time, deleted_at, current_status_id
                ) VALUES (50, 1, 'Activity Migration', NULL, NULL, 1, 1, NULL, 2)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO records (
                    id, item_id, status_id, start_date, end_date, rating, review
                ) VALUES
                    (60, 50, 2, $dayOne, $dayThree, NULL, NULL),
                    (61, 50, 2, $dayTwo, $dayTwo, NULL, NULL)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO activities (date, item_id, record_id) VALUES
                    ($dayOne, 50, 60),
                    ($dayOne, 50, 60)
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE_V5,
            5,
            true,
            LibraryMigrations.MIGRATION_4_5
        ).use { database ->
            database.query(
                """
                SELECT date, record_id
                FROM activities
                WHERE item_id = 50
                ORDER BY date
                """.trimIndent()
            ).use {
                val rows = buildList {
                    while (it.moveToNext()) add(it.getLong(0) to it.getLong(1))
                }
                assertEquals(
                    listOf(
                        dayOne to 60L,
                        dayTwo to 61L,
                        dayThree to 60L
                    ),
                    rows
                )
            }
            database.query(
                """
                SELECT COUNT(*)
                FROM pragma_index_list('activities')
                WHERE name = 'index_activities_item_id_date' AND "unique" = 1
                """.trimIndent()
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals(1, it.getInt(0))
            }
        }
    }

    @Test
    fun migrationFiveToSixBackfillsRecordCreationAtAndPreservesHistory() {
        val startDate = dayMillis(2026, 7, 9)
        helper.createDatabase(TEST_DATABASE_V6, 5).apply {
            execSQL("INSERT INTO item_types (id, name, sort_order) VALUES (1, 'Book', 0)")
            execSQL(
                """
                INSERT INTO statuses (id, name, sort_order, enabled)
                VALUES (1, '想看', 0, 1), (3, '完成', 2, 1)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO items (
                    id, type_id, title, cover_path, thumbnail_path,
                    created_time, updated_time, deleted_at, current_status_id
                ) VALUES (70, 1, 'Timeline Migration', NULL, NULL, 1, 1, NULL, 3)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO records (
                    id, item_id, status_id, start_date, end_date, rating, review
                ) VALUES (80, 70, 3, $startDate, NULL, 9.0, 'preserved')
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE_V6,
            6,
            true,
            LibraryMigrations.MIGRATION_5_6
        ).use { database ->
            database.query(
                "SELECT created_at, status_id, review FROM records WHERE id = 80"
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals(dayNoonMillis(2026, 7, 9), it.getLong(0))
                assertEquals(3L, it.getLong(1))
                assertEquals("preserved", it.getString(2))
            }
            database.query(
                """
                SELECT COUNT(*)
                FROM pragma_index_list('records')
                WHERE name = 'index_records_created_at'
                """.trimIndent()
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals(1, it.getInt(0))
            }
        }
    }

    @Test
    fun migrationSixToSevenRemovesRecordStatusAndPreservesRecordAndActivityData() {
        val startDate = dayMillis(2026, 7, 11)
        val endDate = dayMillis(2026, 7, 13)
        val createdAt = dayNoonMillis(2026, 7, 20)
        helper.createDatabase(TEST_DATABASE_V7, 6).apply {
            execSQL("INSERT INTO item_types (id, name, sort_order) VALUES (1, 'Book', 0)")
            execSQL(
                """
                INSERT INTO statuses (id, name, sort_order, enabled)
                VALUES (1, '想看', 0, 1), (3, '完成', 2, 1)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO items (
                    id, type_id, title, cover_path, thumbnail_path,
                    created_time, updated_time, deleted_at, current_status_id
                ) VALUES (90, 1, 'Statusless Record', NULL, NULL, 1, 1, NULL, 1)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO records (
                    id, item_id, status_id, start_date, end_date,
                    rating, review, created_at
                ) VALUES (
                    91, 90, 3, $startDate, $endDate,
                    8.5, 'preserved review', $createdAt
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO activities (id, date, item_id, record_id)
                VALUES (92, $startDate, 90, 91)
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE_V7,
            7,
            true,
            LibraryMigrations.MIGRATION_6_7
        ).use { database ->
            database.query(
                """
                SELECT item_id, start_date, end_date, rating, review, created_at
                FROM records WHERE id = 91
                """.trimIndent()
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals(90L, it.getLong(0))
                assertEquals(startDate, it.getLong(1))
                assertEquals(endDate, it.getLong(2))
                assertEquals(8.5, it.getDouble(3), 0.0)
                assertEquals("preserved review", it.getString(4))
                assertEquals(createdAt, it.getLong(5))
            }
            database.query("PRAGMA table_info('records')").use {
                val columns = buildList {
                    while (it.moveToNext()) add(it.getString(1))
                }
                assertTrue("status_id" !in columns)
            }
            database.query(
                "SELECT current_status_id FROM items WHERE id = 90"
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals(1L, it.getLong(0))
            }
            database.query(
                "SELECT item_id, record_id FROM activities WHERE id = 92"
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals(90L, it.getLong(0))
                assertEquals(91L, it.getLong(1))
            }
            database.query("PRAGMA foreign_key_check").use {
                assertTrue(!it.moveToFirst())
            }
        }
    }

    @Test
    fun migrationSevenToEightAddsIndependentStableTagOrder() {
        helper.createDatabase(TEST_DATABASE_V8, 7).apply {
            execSQL("INSERT INTO tags (id, name, parent_id, enabled) VALUES (10, '文学', NULL, 1)")
            execSQL("INSERT INTO tags (id, name, parent_id, enabled) VALUES (11, '艺术', NULL, 1)")
            execSQL("INSERT INTO tags (id, name, parent_id, enabled) VALUES (20, '诗歌', 10, 1)")
            execSQL("INSERT INTO tags (id, name, parent_id, enabled) VALUES (21, '小说', 10, 1)")
            execSQL("INSERT INTO tags (id, name, parent_id, enabled) VALUES (30, '绘画', 11, 1)")
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE_V8,
            8,
            true,
            LibraryMigrations.MIGRATION_7_8
        ).use { database ->
            database.query(
                """
                SELECT id, parent_id, sort_order
                FROM tags
                ORDER BY
                    CASE WHEN parent_id IS NULL THEN -1 ELSE parent_id END,
                    sort_order
                """.trimIndent()
            ).use {
                val rows = buildList {
                    while (it.moveToNext()) {
                        add(
                            Triple(
                                it.getLong(0),
                                if (it.isNull(1)) null else it.getLong(1),
                                it.getInt(2)
                            )
                        )
                    }
                }
                assertEquals(
                    listOf(
                        Triple(10L, null, 0),
                        Triple(11L, null, 1),
                        Triple(21L, 10L, 0),
                        Triple(20L, 10L, 1),
                        Triple(30L, 11L, 0)
                    ),
                    rows
                )
            }
        }
    }

    @Test
    fun migrationEightToNineConvertsRatingsAndPreservesActivityLinks() {
        val activityDate = dayMillis(2026, 7, 25)
        helper.createDatabase(TEST_DATABASE_V9, 8).apply {
            execSQL("INSERT INTO item_types (id, name, sort_order) VALUES (1, 'Book', 0)")
            execSQL(
                """
                INSERT INTO statuses (id, name, sort_order, enabled)
                VALUES (1, '想看', 0, 1)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO items (
                    id, type_id, title, cover_path, thumbnail_path,
                    created_time, updated_time, deleted_at, current_status_id
                ) VALUES (100, 1, 'Half Stars', NULL, NULL, 1, 1, NULL, 1)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO records (
                    id, item_id, start_date, end_date, rating, review, created_at
                ) VALUES
                    (101, 100, 1, NULL, NULL, 'none', 1001),
                    (102, 100, 2, NULL, 0.0, 'zero', 1002),
                    (103, 100, 3, NULL, 7.6, 'rounded', 1003),
                    (104, 100, 4, NULL, 8.5, 'half-up', 1004),
                    (105, 100, 5, NULL, 12.0, 'clamped', 1005)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO activities (id, date, item_id, record_id)
                VALUES (201, $activityDate, 100, 104)
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE_V9,
            9,
            true,
            LibraryMigrations.MIGRATION_8_9
        ).use { database ->
            database.query(
                """
                SELECT id, rating_half_stars, review, created_at
                FROM records
                ORDER BY id
                """.trimIndent()
            ).use {
                assertTrue(it.moveToNext())
                assertEquals(101L, it.getLong(0))
                assertTrue(it.isNull(1))
                assertEquals("none", it.getString(2))
                assertEquals(1001L, it.getLong(3))

                assertTrue(it.moveToNext())
                assertEquals(102L, it.getLong(0))
                assertTrue(it.isNull(1))

                assertTrue(it.moveToNext())
                assertEquals(103L, it.getLong(0))
                assertEquals(8, it.getInt(1))

                assertTrue(it.moveToNext())
                assertEquals(104L, it.getLong(0))
                assertEquals(9, it.getInt(1))

                assertTrue(it.moveToNext())
                assertEquals(105L, it.getLong(0))
                assertEquals(10, it.getInt(1))
            }
            database.query(
                "SELECT item_id, record_id FROM activities WHERE id = 201"
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals(100L, it.getLong(0))
                assertEquals(104L, it.getLong(1))
            }
            database.query("PRAGMA table_info('records')").use {
                val columns = buildMap {
                    while (it.moveToNext()) put(it.getString(1), it.getString(2))
                }
                assertTrue("rating" !in columns)
                assertEquals("INTEGER", columns["rating_half_stars"])
            }
            database.query("PRAGMA index_list('records')").use {
                val names = buildList {
                    while (it.moveToNext()) add(it.getString(1))
                }
                assertTrue("index_records_item_id" in names)
                assertTrue("index_records_created_at" in names)
            }
            database.query("PRAGMA foreign_key_check").use {
                assertTrue(!it.moveToFirst())
            }
        }
    }

    @Test
    fun migrationNineToTenAddsEmptyFieldOptionsWithoutChangingDefinitions() {
        helper.createDatabase(TEST_DATABASE_V10, 9).apply {
            execSQL("INSERT INTO item_types (id, name, sort_order) VALUES (1, 'Book', 0)")
            execSQL(
                """
                INSERT INTO field_definitions (
                    id, type_id, name, data_type, enabled, sort_order, is_fixed
                ) VALUES (301, 1, '出版社', 'text', 1, 2, 0)
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE_V10,
            10,
            true,
            LibraryMigrations.MIGRATION_9_10
        ).use { database ->
            database.query(
                """
                SELECT type_id, name, data_type, enabled, sort_order, is_fixed, options
                FROM field_definitions
                WHERE id = 301
                """.trimIndent()
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals(1L, it.getLong(0))
                assertEquals("出版社", it.getString(1))
                assertEquals("text", it.getString(2))
                assertEquals(1, it.getInt(3))
                assertEquals(2, it.getInt(4))
                assertEquals(0, it.getInt(5))
                assertEquals("", it.getString(6))
            }
        }
    }

    @Test
    fun manuallyPopulatedVersionNineFixtureMigratesThroughTwelve() {
        // The historical 9.json/10.json identity hashes are not independently
        // trustworthy. This test therefore treats the v9 SQLite structure and
        // populated rows as the fixture, then validates every real migration.
        helper.createDatabase(TEST_DATABASE_V9_FIXTURE, 9).apply {
            execSQL("INSERT INTO item_types (id, name, sort_order) VALUES (1, 'Book', 0)")
            execSQL(
                "INSERT INTO statuses (id, name, sort_order, enabled) " +
                    "VALUES (91, '历史状态', 4, 1)"
            )
            execSQL(
                """
                INSERT INTO items (
                    id, type_id, title, cover_path, thumbnail_path,
                    current_status_id, created_time, updated_time, deleted_at
                ) VALUES (
                    92, 1, '版本九作品',
                    'images/original/v9.jpg', 'images/thumbnail/v9.jpg',
                    91, 10, 11, NULL
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO field_definitions (
                    id, type_id, name, data_type, enabled, sort_order, is_fixed
                ) VALUES (93, 1, '版本九字段', 'text', 1, 7, 0)
                """.trimIndent()
            )
            execSQL(
                "INSERT INTO field_values (id, item_id, field_id, value) " +
                    "VALUES (94, 92, 93, '版本九字段值')"
            )
            execSQL(
                """
                INSERT INTO records (
                    id, item_id, start_date, end_date,
                    rating_half_stars, review, created_at
                ) VALUES (95, 92, 100, NULL, 8, '版本九记录', 101)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO quotes (
                    id, item_id, content, source, page, created_time
                ) VALUES (96, 92, '版本九摘录', '纸书', '19', 102)
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE_V9_FIXTURE,
            12,
            true,
            LibraryMigrations.MIGRATION_9_10,
            LibraryMigrations.MIGRATION_10_11,
            LibraryMigrations.MIGRATION_11_12
        ).use { database ->
            database.query(
                """
                SELECT current_status_id, cover_path, thumbnail_path
                FROM items WHERE id = 92
                """.trimIndent()
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals(91L, it.getLong(0))
                assertEquals("images/original/v9.jpg", it.getString(1))
                assertEquals("images/thumbnail/v9.jpg", it.getString(2))
            }
            database.query(
                """
                SELECT options, scope, unit, aggregations
                FROM field_definitions WHERE id = 93
                """.trimIndent()
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals("", it.getString(0))
                assertEquals("item", it.getString(1))
                assertTrue(it.isNull(2))
                assertEquals("", it.getString(3))
            }
            database.query(
                """
                SELECT status_snapshot, duration_minutes, review
                FROM records WHERE id = 95
                """.trimIndent()
            ).use {
                assertTrue(it.moveToFirst())
                assertTrue(it.isNull(0))
                assertTrue(it.isNull(1))
                assertEquals("版本九记录", it.getString(2))
            }
            database.query(
                "SELECT content, source, page, chapter FROM quotes WHERE id = 96"
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals("版本九摘录", it.getString(0))
                assertEquals("纸书", it.getString(1))
                assertEquals("19", it.getString(2))
                assertTrue(it.isNull(3))
            }
            database.query("SELECT value FROM field_values WHERE id = 94").use {
                assertTrue(it.moveToFirst())
                assertEquals("版本九字段值", it.getString(0))
            }
            database.query("PRAGMA foreign_key_check").use {
                assertTrue(!it.moveToFirst())
            }
        }
    }

    @Test
    fun migrationTenToElevenKeepsItemFieldsAndAddsRecordScopedStorage() {
        helper.createDatabase(TEST_DATABASE_V11, 10).apply {
            execSQL("INSERT INTO item_types (id, name, sort_order) VALUES (1, 'Book', 0)")
            execSQL(
                """
                INSERT INTO statuses (id, name, sort_order, enabled)
                VALUES (1, '想看', 0, 1)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO items (
                    id, type_id, title, cover_path, thumbnail_path,
                    created_time, updated_time, deleted_at, current_status_id
                ) VALUES (401, 1, '旧作品', NULL, NULL, 1, 1, NULL, 1)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO field_definitions (
                    id, type_id, name, data_type, enabled,
                    sort_order, is_fixed, options
                ) VALUES (402, 1, '字数', 'number', 1, 7, 0, '')
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO field_values (id, item_id, field_id, value)
                VALUES (403, 401, 402, '12.5')
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE_V11,
            11,
            true,
            LibraryMigrations.MIGRATION_10_11
        ).use { database ->
            database.query(
                """
                SELECT scope, unit, aggregations, sort_order
                FROM field_definitions WHERE id = 402
                """.trimIndent()
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals("item", it.getString(0))
                assertTrue(it.isNull(1))
                assertEquals("", it.getString(2))
                assertEquals(7, it.getInt(3))
            }
            database.query(
                "SELECT item_id, field_id, value FROM field_values WHERE id = 403"
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals(401L, it.getLong(0))
                assertEquals(402L, it.getLong(1))
                assertEquals("12.5", it.getString(2))
            }
            database.query(
                "SELECT COUNT(*) FROM record_field_values"
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0))
            }
            database.query("PRAGMA foreign_key_check").use {
                assertTrue(!it.moveToFirst())
            }
        }
    }

    @Test
    fun migrationElevenToTwelveAddsRoundFourSFieldsAndScopes() {
        helper.createDatabase(TEST_DATABASE_V12, 11).apply {
            execSQL("INSERT INTO item_types (id, name, sort_order) VALUES (1, 'Book', 0)")
            execSQL(
                """
                INSERT INTO statuses (id, name, sort_order, enabled)
                VALUES (11, '已完成', 3, 1)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO items (
                    id, type_id, title, cover_path, thumbnail_path,
                    created_time, updated_time, deleted_at, current_status_id
                ) VALUES (
                    21, 1, '迁移作品',
                    'images/original/legacy.jpg',
                    'images/thumbnail/legacy.jpg',
                    1, 1, NULL, 11
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO field_definitions (
                    id, type_id, name, data_type, enabled, sort_order,
                    is_fixed, options, scope, unit, aggregations
                ) VALUES (51, 1, '出版社', 'text', 1, 0, 0, '', 'item', NULL, '')
                """.trimIndent()
            )
            execSQL(
                "INSERT INTO field_values (id, item_id, field_id, value) " +
                    "VALUES (52, 21, 51, '保留字段值')"
            )
            execSQL(
                """
                INSERT INTO records (
                    id, item_id, start_date, end_date,
                    rating_half_stars, review, created_at
                ) VALUES (31, 21, 10, NULL, 8, '保留', 20)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO record_field_values (id, record_id, field_id, value)
                VALUES (53, 31, 51, '历史兼容值')
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO quotes (
                    id, item_id, content, source, page, created_time
                ) VALUES (41, 21, '旧摘录', NULL, '8', 30)
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE_V12,
            12,
            true,
            LibraryMigrations.MIGRATION_11_12
        ).use { database ->
            database.query(
                "SELECT scope, id, name, sort_order FROM statuses WHERE id = 11"
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals("item", it.getString(0))
                assertEquals(11L, it.getLong(1))
                assertEquals("已完成", it.getString(2))
                assertEquals(3, it.getInt(3))
            }
            database.query(
                """
                SELECT status_snapshot, duration_minutes, review
                FROM records WHERE id = 31
                """.trimIndent()
            ).use {
                assertTrue(it.moveToFirst())
                assertTrue(it.isNull(0))
                assertTrue(it.isNull(1))
                assertEquals("保留", it.getString(2))
            }
            database.query("SELECT chapter FROM quotes WHERE id = 41").use {
                assertTrue(it.moveToFirst())
                assertTrue(it.isNull(0))
            }
            database.query("SELECT current_status_id FROM items WHERE id = 21").use {
                assertTrue(it.moveToFirst())
                assertEquals(11L, it.getLong(0))
            }
            database.query(
                "SELECT cover_path, thumbnail_path FROM items WHERE id = 21"
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals("images/original/legacy.jpg", it.getString(0))
                assertEquals("images/thumbnail/legacy.jpg", it.getString(1))
            }
            database.query(
                "SELECT value FROM field_values WHERE id = 52"
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals("保留字段值", it.getString(0))
            }
            database.query(
                "SELECT value FROM record_field_values WHERE id = 53"
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals("历史兼容值", it.getString(0))
            }
            database.execSQL(
                """
                INSERT INTO statuses (id, name, sort_order, enabled, scope)
                VALUES (12, '已完成', 0, 1, 'record')
                """.trimIndent()
            )
            assertTrue(
                runCatching {
                    database.execSQL(
                        """
                        INSERT INTO statuses (id, name, sort_order, enabled, scope)
                        VALUES (13, '已完成', 1, 1, 'record')
                        """.trimIndent()
                    )
                }.isFailure
            )
            database.query("PRAGMA foreign_key_check").use {
                assertTrue(!it.moveToFirst())
            }
        }
    }

    @Test
    fun completeMigrationChainReachesSchemaTwelveWithoutDataLoss() {
        helper.createDatabase(TEST_DATABASE_ALL, 1).apply {
            execSQL("INSERT INTO item_types (id, name, sort_order) VALUES (1, 'Book', 0)")
            execSQL(
                """
                INSERT INTO items (
                    id, type_id, title, cover_path, thumbnail_path,
                    created_time, updated_time
                ) VALUES (
                    71, 1, '完整迁移',
                    'images/original/v1.jpg',
                    'images/thumbnail/v1.jpg',
                    100, 100
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO field_definitions (
                    id, type_id, name, data_type, enabled, sort_order
                ) VALUES (73, 1, '旧字段', 'text', 1, 0)
                """.trimIndent()
            )
            execSQL(
                "INSERT INTO field_values (id, item_id, field_id, value) " +
                    "VALUES (74, 71, 73, '从版本一保留')"
            )
            execSQL(
                """
                INSERT INTO quotes (
                    id, item_id, content, source, page, created_time
                ) VALUES (75, 71, '版本一摘录', '纸书', '9', 101)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO records (
                    id, item_id, start_date, end_date, rating, review
                ) VALUES (72, 71, 100, NULL, 8.0, '保留到十二')
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE_ALL,
            12,
            true,
            *LibraryMigrations.ALL
        ).use { database ->
            database.query(
                """
                SELECT item_id, review, status_snapshot, duration_minutes
                FROM records
                WHERE id = 72
                """.trimIndent()
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals(71L, it.getLong(0))
                assertEquals("保留到十二", it.getString(1))
                assertTrue(it.isNull(2))
                assertTrue(it.isNull(3))
            }
            database.query(
                "SELECT scope FROM statuses ORDER BY id LIMIT 1"
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals("item", it.getString(0))
            }
            database.query(
                "SELECT cover_path, thumbnail_path FROM items WHERE id = 71"
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals("images/original/v1.jpg", it.getString(0))
                assertEquals("images/thumbnail/v1.jpg", it.getString(1))
            }
            database.query("SELECT value FROM field_values WHERE id = 74").use {
                assertTrue(it.moveToFirst())
                assertEquals("从版本一保留", it.getString(0))
            }
            database.query(
                "SELECT content, source, page, chapter FROM quotes WHERE id = 75"
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals("版本一摘录", it.getString(0))
                assertEquals("纸书", it.getString(1))
                assertEquals("9", it.getString(2))
                assertTrue(it.isNull(3))
            }
            database.query("PRAGMA foreign_key_check").use {
                assertTrue(!it.moveToFirst())
            }
        }
    }

    private fun dayMillis(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    private fun dayNoonMillis(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day)
            .atTime(12, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    private companion object {
        const val TEST_DATABASE = "library-migration-test"
        const val TEST_DATABASE_V3 = "library-migration-v3-test"
        const val TEST_DATABASE_V4 = "library-migration-v4-test"
        const val TEST_DATABASE_V5 = "library-migration-v5-test"
        const val TEST_DATABASE_V6 = "library-migration-v6-test"
        const val TEST_DATABASE_V7 = "library-migration-v7-test"
        const val TEST_DATABASE_V8 = "library-migration-v8-test"
        const val TEST_DATABASE_V9 = "library-migration-v9-test"
        const val TEST_DATABASE_V9_FIXTURE = "library-migration-v9-fixture-test"
        const val TEST_DATABASE_V10 = "library-migration-v10-test"
        const val TEST_DATABASE_V11 = "library-migration-v11-test"
        const val TEST_DATABASE_V12 = "library-migration-v12-test"
        const val TEST_DATABASE_ALL = "library-migration-all-test"
    }
}
