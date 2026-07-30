package com.example.mylibrary.ui.theme.importer

import com.example.mylibrary.ui.navigation.ThemeIconRendering
import com.example.mylibrary.ui.theme.FontRole
import com.example.mylibrary.ui.theme.FontSlot
import com.example.mylibrary.ui.theme.NavigationIconDefinition
import com.example.mylibrary.ui.theme.ThemeColorManifest
import com.example.mylibrary.ui.theme.ThemeFontManifest
import com.example.mylibrary.ui.theme.ThemeManifest
import com.example.mylibrary.ui.theme.ThemeNavigationManifest
import com.example.mylibrary.ui.theme.ThemeSurfaceDefinition
import com.example.mylibrary.ui.theme.ThemeSurfaceManifest
import com.example.mylibrary.ui.theme.ThemeSurfaceType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Locale

class ThemePackageJsonCodec(
    private val json: Json = Json {
        prettyPrint = true
    }
) {
    fun decodeManifest(text: String): ThemePackageResult<ThemeManifest> =
        try {
            StrictJsonDuplicateKeyValidator.validate(text)
            val root = json.parseToJsonElement(text).requireObject("manifest")
            root.requireKeys(
                required = setOf(
                    "schemaVersion",
                    "id",
                    "name",
                    "author",
                    "version",
                    "surfaces",
                    "colors",
                    "fonts",
                    "fontAssignments",
                    "navigationIcons",
                    "darkSystemBarIcons"
                )
            )
            ThemePackageResult.Success(
                ThemeManifest(
                    schemaVersion = root.requireInt("schemaVersion"),
                    id = root.requireString("id"),
                    name = root.requireString("name"),
                    author = root.optionalString("author"),
                    version = root.requireString("version"),
                    surfaces = decodeSurfaces(root.requireObject("surfaces")),
                    colors = decodeColors(root.requireObject("colors")),
                    fonts = decodeFonts(root.requireObject("fonts")),
                    fontAssignments = decodeFontAssignments(
                        root.requireObject("fontAssignments")
                    ),
                    navigationIcons = decodeNavigation(root["navigationIcons"]),
                    darkSystemBarIcons =
                        root.requireBoolean("darkSystemBarIcons")
                )
            )
        } catch (exception: Exception) {
            ThemePackageResult.Failure(
                ThemePackageError.ManifestParseFailed(
                    exception.message ?: exception::class.java.simpleName
                )
            )
        }

    fun decodeChecksums(
        text: String
    ): ThemePackageResult<ThemeChecksumManifest> =
        try {
            StrictJsonDuplicateKeyValidator.validate(text)
            val root = json.parseToJsonElement(text).requireObject("checksums")
            root.requireKeys(required = setOf("algorithm", "files"))
            val filesObject = root.requireObject("files")
            val files = linkedMapOf<String, String>()
            val casePaths = mutableMapOf<String, String>()
            filesObject.forEach { (path, value) ->
                ThemeArchivePathPolicy.validate(
                    path,
                    ThemeArchiveEntryKind.FILE
                )
                val digest = value.requireString("checksum for $path")
                if (!LOWERCASE_SHA256.matches(digest)) {
                    error("Checksum for $path must be 64 lowercase hex characters")
                }
                val prior = casePaths.putIfAbsent(
                    path.lowercase(Locale.ROOT),
                    path
                )
                if (prior != null && prior != path) {
                    error("Checksum paths collide by case: $prior and $path")
                }
                files[path] = digest
            }
            ThemePackageResult.Success(
                ThemeChecksumManifest(
                    algorithm = root.requireString("algorithm"),
                    files = files
                )
            )
        } catch (exception: Exception) {
            ThemePackageResult.Failure(
                ThemePackageError.ChecksumsInvalid(
                    exception.message ?: exception::class.java.simpleName
                )
            )
        }

    fun encodeManifest(manifest: ThemeManifest): String =
        json.encodeToString(
            JsonElement.serializer(),
            buildJsonObject {
                put("schemaVersion", manifest.schemaVersion)
                put("id", manifest.id)
                put("name", manifest.name)
                put(
                    "author",
                    manifest.author?.let { JsonPrimitive(it) } ?: JsonNull
                )
                put("version", manifest.version)
                put("surfaces", encodeSurfaces(manifest.surfaces))
                put("colors", encodeColors(manifest.colors))
                put("fonts", encodeFonts(manifest.fonts))
                put(
                    "fontAssignments",
                    buildJsonObject {
                        FontRole.entries.forEach { role ->
                            put(
                                role.name,
                                manifest.fontAssignments.getValue(role).name
                            )
                        }
                    }
                )
                put(
                    "navigationIcons",
                    manifest.navigationIcons?.let(::encodeNavigation) ?: JsonNull
                )
                put("darkSystemBarIcons", manifest.darkSystemBarIcons)
            }
        )

    fun encodeChecksums(checksums: ThemeChecksumManifest): String =
        json.encodeToString(
            JsonElement.serializer(),
            buildJsonObject {
                put("algorithm", checksums.algorithm)
                put(
                    "files",
                    buildJsonObject {
                        checksums.files.toSortedMap().forEach { (path, digest) ->
                            put(path, digest)
                        }
                    }
                )
            }
        )

    private fun decodeSurfaces(root: JsonObject): ThemeSurfaceManifest {
        root.requireKeys(required = setOf("background", "card", "dialog"))
        return ThemeSurfaceManifest(
            background = decodeSurface(root.requireObject("background")),
            card = decodeSurface(root.requireObject("card")),
            dialog = decodeSurface(root.requireObject("dialog"))
        )
    }

    private fun decodeSurface(root: JsonObject): ThemeSurfaceDefinition {
        root.requireKeys(required = setOf("type", "color", "file"))
        return ThemeSurfaceDefinition(
            type = enumValueOf<ThemeSurfaceType>(root.requireString("type")),
            color = root.requireString("color"),
            file = root.optionalString("file")
        )
    }

    private fun decodeColors(root: JsonObject): ThemeColorManifest {
        root.requireKeys(
            required = setOf(
                "textPrimary",
                "textSecondary",
                "border",
                "accent",
                "onAccent"
            )
        )
        return ThemeColorManifest(
            textPrimary = root.requireString("textPrimary"),
            textSecondary = root.requireString("textSecondary"),
            border = root.requireString("border"),
            accent = root.requireString("accent"),
            onAccent = root.requireString("onAccent")
        )
    }

    private fun decodeFonts(root: JsonObject): ThemeFontManifest {
        root.requireKeys(required = setOf("fontA", "fontB"))
        return ThemeFontManifest(
            fontA = root.optionalString("fontA"),
            fontB = root.optionalString("fontB")
        )
    }

    private fun decodeFontAssignments(
        root: JsonObject
    ): Map<FontRole, FontSlot> {
        root.requireKeys(required = FontRole.entries.mapTo(linkedSetOf()) { it.name })
        return FontRole.entries.associateWith { role ->
            enumValueOf<FontSlot>(root.requireString(role.name))
        }
    }

    private fun decodeNavigation(element: JsonElement?): ThemeNavigationManifest? {
        if (element == null || element is JsonNull) return null
        val root = element.requireObject("navigationIcons")
        root.requireKeys(
            required = setOf("rendering"),
            optional = setOf("home", "library", "statistics", "settings")
        )
        return ThemeNavigationManifest(
            rendering = ThemeIconRendering.fromManifestValue(
                root.requireString("rendering")
            ) ?: error("Unsupported navigation rendering"),
            home = root.optionalDefinition("home"),
            library = root.optionalDefinition("library"),
            statistics = root.optionalDefinition("statistics"),
            settings = root.optionalDefinition("settings")
        )
    }

    private fun JsonObject.optionalDefinition(
        name: String
    ): NavigationIconDefinition? {
        val element = this[name] ?: return null
        if (element is JsonNull) return null
        val root = element.requireObject(name)
        root.requireKeys(
            required = setOf("normal"),
            optional = setOf("selected")
        )
        return NavigationIconDefinition(
            normal = root.requireString("normal"),
            selected = root.optionalString("selected")
        )
    }

    private fun encodeSurfaces(surfaces: ThemeSurfaceManifest): JsonObject =
        buildJsonObject {
            put("background", encodeSurface(surfaces.background))
            put("card", encodeSurface(surfaces.card))
            put("dialog", encodeSurface(surfaces.dialog))
        }

    private fun encodeSurface(surface: ThemeSurfaceDefinition): JsonObject =
        buildJsonObject {
            put("type", surface.type.name)
            put("color", surface.color)
            put(
                "file",
                surface.file?.let { JsonPrimitive(it) } ?: JsonNull
            )
        }

    private fun encodeColors(colors: ThemeColorManifest): JsonObject =
        buildJsonObject {
            put("textPrimary", colors.textPrimary)
            put("textSecondary", colors.textSecondary)
            put("border", colors.border)
            put("accent", colors.accent)
            put("onAccent", colors.onAccent)
        }

    private fun encodeFonts(fonts: ThemeFontManifest): JsonObject =
        buildJsonObject {
            put(
                "fontA",
                fonts.fontA?.let { JsonPrimitive(it) } ?: JsonNull
            )
            put(
                "fontB",
                fonts.fontB?.let { JsonPrimitive(it) } ?: JsonNull
            )
        }

    private fun encodeNavigation(
        navigation: ThemeNavigationManifest
    ): JsonObject = buildJsonObject {
        put("rendering", navigation.rendering.name)
        navigation.home?.let { put("home", encodeDefinition(it)) }
        navigation.library?.let { put("library", encodeDefinition(it)) }
        navigation.statistics?.let {
            put("statistics", encodeDefinition(it))
        }
        navigation.settings?.let { put("settings", encodeDefinition(it)) }
    }

    private fun encodeDefinition(
        definition: NavigationIconDefinition
    ): JsonObject = buildJsonObject {
        put("normal", definition.normal)
        put(
            "selected",
            definition.selected?.let { JsonPrimitive(it) } ?: JsonNull
        )
    }

    private companion object {
        val LOWERCASE_SHA256 = Regex("^[0-9a-f]{64}$")
    }
}

private fun JsonElement.requireObject(name: String): JsonObject =
    this as? JsonObject ?: error("$name must be a JSON object")

private fun JsonObject.requireObject(name: String): JsonObject =
    this[name]?.requireObject(name) ?: error("$name is required")

private fun JsonObject.requireKeys(
    required: Set<String>,
    optional: Set<String> = emptySet()
) {
    val missing = required - keys
    if (missing.isNotEmpty()) error("Missing fields: ${missing.sorted()}")
    val unexpected = keys - required - optional
    if (unexpected.isNotEmpty()) {
        error("Unexpected fields: ${unexpected.sorted()}")
    }
}

private fun JsonObject.requireString(name: String): String =
    this[name]?.requireString(name) ?: error("$name is required")

private fun JsonElement.requireString(name: String): String {
    val primitive = this as? JsonPrimitive
        ?: error("$name must be a string")
    if (!primitive.isString) error("$name must be a string")
    return primitive.content
}

private fun JsonObject.optionalString(name: String): String? {
    val element = this[name] ?: return null
    if (element is JsonNull) return null
    return element.requireString(name)
}

private fun JsonObject.requireInt(name: String): Int {
    val primitive = this[name] as? JsonPrimitive
        ?: error("$name must be an integer")
    if (primitive.isString) error("$name must be an integer")
    return primitive.content.toIntOrNull()
        ?: error("$name must be an integer")
}

private fun JsonObject.requireBoolean(name: String): Boolean {
    val primitive = this[name] as? JsonPrimitive
        ?: error("$name must be a Boolean")
    if (primitive.isString) error("$name must be a Boolean")
    return when (primitive.content) {
        "true" -> true
        "false" -> false
        else -> error("$name must be a Boolean")
    }
}

private object StrictJsonDuplicateKeyValidator {
    fun validate(text: String) {
        Parser(text).validate()
    }

    private class Parser(
        private val source: String
    ) {
        private var index = 0

        fun validate() {
            skipWhitespace()
            parseValue()
            skipWhitespace()
            if (index != source.length) error("Unexpected JSON trailing data")
        }

        private fun parseValue() {
            skipWhitespace()
            when (peek()) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> consumeLiteral("true")
                'f' -> consumeLiteral("false")
                'n' -> consumeLiteral("null")
                '-', in '0'..'9' -> parseNumber()
                else -> error("Invalid JSON value at $index")
            }
        }

        private fun parseObject() {
            consume('{')
            skipWhitespace()
            if (tryConsume('}')) return
            val keys = mutableSetOf<String>()
            while (true) {
                skipWhitespace()
                val key = parseString()
                if (!keys.add(key)) error("Duplicate JSON key: $key")
                skipWhitespace()
                consume(':')
                parseValue()
                skipWhitespace()
                if (tryConsume('}')) return
                consume(',')
            }
        }

        private fun parseArray() {
            consume('[')
            skipWhitespace()
            if (tryConsume(']')) return
            while (true) {
                parseValue()
                skipWhitespace()
                if (tryConsume(']')) return
                consume(',')
            }
        }

        private fun parseString(): String {
            consume('"')
            val result = StringBuilder()
            while (index < source.length) {
                val character = source[index++]
                when (character) {
                    '"' -> return result.toString()
                    '\\' -> result.append(parseEscape())
                    else -> {
                        if (character.code < 0x20) {
                            error("Control character in JSON string")
                        }
                        result.append(character)
                    }
                }
            }
            error("Unterminated JSON string")
        }

        private fun parseEscape(): Char {
            if (index >= source.length) error("Unterminated JSON escape")
            return when (val escaped = source[index++]) {
                '"', '\\', '/' -> escaped
                'b' -> '\b'
                'f' -> '\u000C'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'u' -> {
                    if (index + 4 > source.length) {
                        error("Invalid Unicode escape")
                    }
                    val value = source.substring(index, index + 4)
                        .toIntOrNull(16)
                        ?: error("Invalid Unicode escape")
                    index += 4
                    value.toChar()
                }
                else -> error("Invalid JSON escape: $escaped")
            }
        }

        private fun parseNumber() {
            val start = index
            while (
                index < source.length &&
                source[index] !in charArrayOf(',', '}', ']', ' ', '\t', '\r', '\n')
            ) {
                index += 1
            }
            if (index == start) error("Invalid JSON number")
        }

        private fun consumeLiteral(value: String) {
            if (!source.startsWith(value, index)) {
                error("Invalid JSON literal at $index")
            }
            index += value.length
        }

        private fun consume(expected: Char) {
            skipWhitespace()
            if (peek() != expected) {
                error("Expected '$expected' at $index")
            }
            index += 1
        }

        private fun tryConsume(expected: Char): Boolean {
            skipWhitespace()
            if (peek() != expected) return false
            index += 1
            return true
        }

        private fun peek(): Char =
            source.getOrNull(index) ?: error("Unexpected end of JSON")

        private fun skipWhitespace() {
            while (
                index < source.length &&
                source[index] in charArrayOf(' ', '\t', '\r', '\n')
            ) {
                index += 1
            }
        }
    }
}
