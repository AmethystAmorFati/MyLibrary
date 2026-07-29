package com.example.mylibrary.backup.serialization

import com.example.mylibrary.backup.model.CURRENT_BACKUP_SCHEMA_VERSION
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

interface BackupMigration {
    val fromVersion: Int
    val toVersion: Int

    fun migrate(source: JsonObject): JsonObject
}

class BackupMigrationChain(
    migrations: List<BackupMigration> = listOf(
        BackupMigration1To2,
        BackupMigration2To3,
        BackupMigration3To4
    )
) {
    private val migrationsByVersion = migrations.associateBy { it.fromVersion }

    fun migrate(source: JsonObject, sourceVersion: Int): JsonObject {
        require(sourceVersion > 0) { "Invalid backup schema version" }
        require(sourceVersion <= CURRENT_BACKUP_SCHEMA_VERSION) {
            "Backup schema is newer than this app"
        }
        var currentVersion = sourceVersion
        var current = source
        while (currentVersion < CURRENT_BACKUP_SCHEMA_VERSION) {
            val migration = migrationsByVersion[currentVersion]
                ?: error("No backup migration from version $currentVersion")
            require(migration.toVersion > migration.fromVersion) {
                "Backup migration must advance the version"
            }
            current = migration.migrate(current)
            currentVersion = migration.toVersion
        }
        require(currentVersion == CURRENT_BACKUP_SCHEMA_VERSION) {
            "Backup migration chain did not reach the current version"
        }
        return current
    }
}

object BackupMigration3To4 : BackupMigration {
    override val fromVersion: Int = 3
    override val toVersion: Int = 4

    override fun migrate(source: JsonObject): JsonObject {
        fun addDefault(
            arrayName: String,
            propertyName: String,
            defaultValue: kotlinx.serialization.json.JsonElement
        ): JsonArray {
            val values = source[arrayName]?.jsonArray ?: JsonArray(emptyList())
            return JsonArray(
                values.map { element ->
                    val value = element.jsonObject
                    JsonObject(value + (propertyName to (value[propertyName] ?: defaultValue)))
                }
            )
        }
        return JsonObject(
            source +
                ("quotes" to addDefault(
                    "quotes",
                    "chapter",
                    kotlinx.serialization.json.JsonNull
                )) +
                ("records" to JsonArray(
                    addDefault(
                        "records",
                        "statusSnapshot",
                        kotlinx.serialization.json.JsonNull
                    ).map { element ->
                        val record = element.jsonObject
                        JsonObject(
                            record + (
                                "durationMinutes" to (
                                    record["durationMinutes"]
                                        ?: kotlinx.serialization.json.JsonNull
                                )
                            )
                        )
                    }
                )) +
                ("statuses" to addDefault(
                    "statuses",
                    "scope",
                    JsonPrimitive("item")
                ))
        )
    }
}

object BackupMigration2To3 : BackupMigration {
    override val fromVersion: Int = 2
    override val toVersion: Int = 3

    override fun migrate(source: JsonObject): JsonObject {
        val fields = source["fieldDefinitions"]?.jsonArray ?: JsonArray(emptyList())
        val migratedFields = JsonArray(
            fields.map { element ->
                val field = element.jsonObject
                JsonObject(
                    field +
                        ("scope" to (field["scope"] ?: JsonPrimitive("item"))) +
                        ("unit" to (field["unit"] ?: kotlinx.serialization.json.JsonNull)) +
                        (
                            "aggregations" to (
                                field["aggregations"] ?: JsonArray(emptyList())
                            )
                        )
                )
            }
        )
        return JsonObject(
            source +
                ("fieldDefinitions" to migratedFields) +
                (
                    "recordFieldValues" to (
                        source["recordFieldValues"] ?: JsonArray(emptyList())
                    )
                )
        )
    }
}

object BackupMigration1To2 : BackupMigration {
    override val fromVersion: Int = 1
    override val toVersion: Int = 2

    override fun migrate(source: JsonObject): JsonObject {
        val fields = source["fieldDefinitions"]?.jsonArray ?: return source
        val migratedFields = JsonArray(
            fields.map { element ->
                val field = element.jsonObject
                val options = field["options"]?.jsonArray ?: JsonArray(emptyList())
                JsonObject(
                    field + (
                        "options" to JsonArray(
                            options.mapIndexed { index, option ->
                                if (option is JsonObject) {
                                    option
                                } else {
                                    buildJsonObject {
                                        put("id", index + 1L)
                                        put(
                                            "name",
                                            (option as? JsonPrimitive)?.content.orEmpty()
                                        )
                                        put("isActive", true)
                                        put("sortOrder", index)
                                    }
                                }
                            }
                        )
                    )
                )
            }
        )
        return JsonObject(source + ("fieldDefinitions" to migratedFields))
    }
}
