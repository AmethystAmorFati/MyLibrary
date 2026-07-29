package com.example.mylibrary.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object LibraryMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE items ADD COLUMN deleted_at INTEGER")
            db.execSQL("CREATE INDEX index_items_deleted_at ON items (deleted_at)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS statuses (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    sort_order INTEGER NOT NULL,
                    enabled INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_statuses_name ON statuses (name)"
            )
            insertDefaultStatuses(db)

            db.execSQL(
                """
                ALTER TABLE records
                ADD COLUMN status_id INTEGER
                    REFERENCES statuses(id)
                    ON UPDATE NO ACTION ON DELETE RESTRICT
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX index_records_status_id ON records (status_id)")
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE field_definitions " +
                    "ADD COLUMN is_fixed INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL(
                """
                UPDATE field_definitions
                SET is_fixed = 1
                WHERE (type_id = 1 AND name = 'author')
                   OR (type_id = 2 AND name = 'director')
                """.trimIndent()
            )
            db.execSQL(
                "ALTER TABLE tags " +
                    "ADD COLUMN enabled INTEGER NOT NULL DEFAULT 1"
            )
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                ALTER TABLE items
                ADD COLUMN current_status_id INTEGER
                    REFERENCES statuses(id)
                    ON UPDATE NO ACTION ON DELETE RESTRICT
                """.trimIndent()
            )
            db.execSQL(
                """
                UPDATE items
                SET current_status_id = (
                    SELECT record.status_id
                    FROM records record
                    WHERE record.item_id = items.id
                      AND record.status_id IS NOT NULL
                    ORDER BY record.start_date DESC, record.id DESC
                    LIMIT 1
                )
                """.trimIndent()
            )
            db.execSQL(
                "UPDATE items SET current_status_id = 1 WHERE current_status_id IS NULL"
            )
            db.execSQL(
                "CREATE INDEX index_items_current_status_id ON items (current_status_id)"
            )
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Activity is a derived projection of records. Rebuilding it avoids
            // carrying forward partial or duplicate rows from the placeholder era.
            db.execSQL("DELETE FROM activities")
            db.execSQL(
                """
                WITH RECURSIVE expanded(record_id, item_id, activity_date, end_date) AS (
                    SELECT
                        id,
                        item_id,
                        start_date,
                        CASE
                            WHEN end_date IS NULL OR end_date < start_date
                                THEN start_date
                            ELSE end_date
                        END
                    FROM records
                    UNION ALL
                    SELECT
                        record_id,
                        item_id,
                        CAST(
                            strftime(
                                '%s',
                                date(activity_date / 1000, 'unixepoch', 'localtime', '+1 day'),
                                'utc'
                            ) AS INTEGER
                        ) * 1000,
                        end_date
                    FROM expanded
                    WHERE activity_date < end_date
                )
                INSERT INTO activities(date, item_id, record_id)
                SELECT activity_date, item_id, MAX(record_id)
                FROM expanded
                GROUP BY item_id, activity_date
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS index_activities_item_id_date
                ON activities (item_id, date)
                """.trimIndent()
            )
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                ALTER TABLE records
                ADD COLUMN created_at INTEGER NOT NULL DEFAULT 0
                """.trimIndent()
            )
            // Older records have no recoverable creation timestamp. Use the
            // start date's local noon so the compatibility value stays on the
            // same local calendar day, including around time-zone boundaries.
            db.execSQL(
                """
                UPDATE records
                SET created_at = CAST(
                    strftime(
                        '%s',
                        date(start_date / 1000, 'unixepoch', 'localtime')
                            || ' 12:00:00',
                        'utc'
                    ) AS INTEGER
                ) * 1000
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_records_created_at
                ON records (created_at)
                """.trimIndent()
            )
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS records_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    item_id INTEGER NOT NULL,
                    start_date INTEGER NOT NULL,
                    end_date INTEGER,
                    rating REAL,
                    review TEXT,
                    created_at INTEGER NOT NULL,
                    FOREIGN KEY(item_id) REFERENCES items(id)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO records_new (
                    id, item_id, start_date, end_date, rating, review, created_at
                )
                SELECT
                    id, item_id, start_date, end_date, rating, review, created_at
                FROM records
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TEMP TABLE activities_backup AS
                SELECT id, date, item_id, record_id FROM activities
                """.trimIndent()
            )
            db.execSQL("DROP TABLE activities")
            db.execSQL("DROP TABLE records")
            db.execSQL("ALTER TABLE records_new RENAME TO records")
            db.execSQL("CREATE INDEX index_records_item_id ON records (item_id)")
            db.execSQL("CREATE INDEX index_records_created_at ON records (created_at)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS activities (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    date INTEGER NOT NULL,
                    item_id INTEGER NOT NULL,
                    record_id INTEGER,
                    FOREIGN KEY(item_id) REFERENCES items(id)
                        ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(record_id) REFERENCES records(id)
                        ON UPDATE NO ACTION ON DELETE SET NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO activities (id, date, item_id, record_id)
                SELECT id, date, item_id, record_id FROM activities_backup
                """.trimIndent()
            )
            db.execSQL("DROP TABLE activities_backup")
            db.execSQL("CREATE INDEX index_activities_date ON activities (date)")
            db.execSQL("CREATE INDEX index_activities_item_id ON activities (item_id)")
            db.execSQL("CREATE INDEX index_activities_record_id ON activities (record_id)")
            db.execSQL(
                """
                CREATE UNIQUE INDEX index_activities_item_id_date
                ON activities (item_id, date)
                """.trimIndent()
            )
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                ALTER TABLE tags
                ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0
                """.trimIndent()
            )
            // Preserve the previous user-visible name order independently for
            // roots and for every child group.
            db.execSQL(
                """
                UPDATE tags
                SET sort_order = (
                    SELECT COUNT(*)
                    FROM tags peer
                    WHERE (
                        (peer.parent_id IS NULL AND tags.parent_id IS NULL)
                        OR peer.parent_id = tags.parent_id
                    )
                    AND (
                        peer.name < tags.name
                        OR (
                            peer.name = tags.name
                            AND peer.id < tags.id
                        )
                    )
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS records_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    item_id INTEGER NOT NULL,
                    start_date INTEGER NOT NULL,
                    end_date INTEGER,
                    rating_half_stars INTEGER,
                    review TEXT,
                    created_at INTEGER NOT NULL,
                    FOREIGN KEY(item_id) REFERENCES items(id)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO records_new (
                    id, item_id, start_date, end_date,
                    rating_half_stars, review, created_at
                )
                SELECT
                    id,
                    item_id,
                    start_date,
                    end_date,
                    CASE
                        WHEN rating IS NULL OR rating <= 0 THEN NULL
                        WHEN rating >= 10 THEN 10
                        ELSE CAST(round(rating) AS INTEGER)
                    END,
                    review,
                    created_at
                FROM records
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TEMP TABLE activities_backup AS
                SELECT id, date, item_id, record_id FROM activities
                """.trimIndent()
            )
            db.execSQL("DROP TABLE activities")
            db.execSQL("DROP TABLE records")
            db.execSQL("ALTER TABLE records_new RENAME TO records")
            db.execSQL("CREATE INDEX index_records_item_id ON records (item_id)")
            db.execSQL("CREATE INDEX index_records_created_at ON records (created_at)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS activities (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    date INTEGER NOT NULL,
                    item_id INTEGER NOT NULL,
                    record_id INTEGER,
                    FOREIGN KEY(item_id) REFERENCES items(id)
                        ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(record_id) REFERENCES records(id)
                        ON UPDATE NO ACTION ON DELETE SET NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO activities (id, date, item_id, record_id)
                SELECT id, date, item_id, record_id FROM activities_backup
                """.trimIndent()
            )
            db.execSQL("DROP TABLE activities_backup")
            db.execSQL("CREATE INDEX index_activities_date ON activities (date)")
            db.execSQL("CREATE INDEX index_activities_item_id ON activities (item_id)")
            db.execSQL("CREATE INDEX index_activities_record_id ON activities (record_id)")
            db.execSQL(
                """
                CREATE UNIQUE INDEX index_activities_item_id_date
                ON activities (item_id, date)
                """.trimIndent()
            )
        }
    }

    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                ALTER TABLE field_definitions
                ADD COLUMN options TEXT NOT NULL DEFAULT ''
                """.trimIndent()
            )
        }
    }

    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE field_definitions " +
                    "ADD COLUMN scope TEXT NOT NULL DEFAULT 'item'"
            )
            db.execSQL("ALTER TABLE field_definitions ADD COLUMN unit TEXT")
            db.execSQL(
                "ALTER TABLE field_definitions " +
                    "ADD COLUMN aggregations TEXT NOT NULL DEFAULT ''"
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS record_field_values (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    record_id INTEGER NOT NULL,
                    field_id INTEGER NOT NULL,
                    value TEXT NOT NULL,
                    FOREIGN KEY(record_id) REFERENCES records(id)
                        ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(field_id) REFERENCES field_definitions(id)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX index_record_field_values_record_id " +
                    "ON record_field_values (record_id)"
            )
            db.execSQL(
                "CREATE INDEX index_record_field_values_field_id " +
                    "ON record_field_values (field_id)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX index_record_field_values_record_id_field_id " +
                    "ON record_field_values (record_id, field_id)"
            )
        }
    }

    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE quotes ADD COLUMN chapter TEXT")
            db.execSQL("ALTER TABLE records ADD COLUMN status_snapshot TEXT")
            db.execSQL("ALTER TABLE records ADD COLUMN duration_minutes INTEGER")
            db.execSQL(
                "ALTER TABLE statuses ADD COLUMN scope TEXT NOT NULL DEFAULT 'item'"
            )
            db.execSQL("DROP INDEX IF EXISTS index_statuses_name")
            db.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS index_statuses_scope_name
                ON statuses(scope, name)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_statuses_scope_sort_order
                ON statuses(scope, sort_order)
                """.trimIndent()
            )
            db.query("PRAGMA foreign_key_check").use { cursor ->
                check(!cursor.moveToFirst()) {
                    "Foreign key check failed after migration 11 to 12"
                }
            }
        }
    }

    val ALL: Array<Migration> =
        arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10,
            MIGRATION_10_11,
            MIGRATION_11_12
        )
}
