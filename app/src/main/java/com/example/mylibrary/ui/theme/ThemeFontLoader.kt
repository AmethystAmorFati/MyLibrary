package com.example.mylibrary.ui.theme

import android.graphics.Typeface
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.text.font.FontFamily
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.LinkOption

internal data class ValidatedThemeFontFile(
    val slot: FontSlot,
    val relativePath: String,
    val file: File,
    val size: Long,
    val lastModified: Long
)

internal sealed interface ThemeFontFileValidationResult {
    data class Success(
        val files: Map<FontSlot, ValidatedThemeFontFile>
    ) : ThemeFontFileValidationResult

    data class Failure(
        val error: ThemeResolveError
    ) : ThemeFontFileValidationResult
}

internal object ThemeFontFileValidator {
    private const val TRUE_TYPE_SFNT = 0x00010000
    private const val APPLE_TRUE_TYPE_SFNT = 0x74727565
    private const val OPEN_TYPE_CFF = 0x4F54544F
    private const val TRUE_TYPE_COLLECTION = 0x74746366
    private const val WOFF = 0x774F4646
    private const val WOFF2 = 0x774F4632
    private const val MAX_TABLE_COUNT = 4096
    private val requiredTables = setOf("head", "maxp", "cmap", "name")

    fun validateDeclaredFiles(
        fonts: ThemeFontManifest,
        resources: ThemeResourceProvider
    ): ThemeFontFileValidationResult {
        val declarations = listOfNotNull(
            fonts.fontA?.let { FontSlot.A to it },
            fonts.fontB?.let { FontSlot.B to it }
        )

        val files = linkedMapOf<FontSlot, ValidatedThemeFontFile>()
        for ((slot, relativePath) in declarations) {
            val extension = relativePath.substringAfterLast('.', "").lowercase()
            if (extension !in ThemeResourceLimits.FONT_EXTENSIONS) {
                return ThemeFontFileValidationResult.Failure(
                    ThemeResolveError.UnsupportedFontFormat(relativePath)
                )
            }

            val file = try {
                resources.resolveFile(relativePath)
            } catch (exception: ThemeResourceAccessException) {
                return ThemeFontFileValidationResult.Failure(exception.error)
            }
            if (!Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                return ThemeFontFileValidationResult.Failure(
                    ThemeResolveError.ResourceMissing(relativePath)
                )
            }
            if (!Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                return ThemeFontFileValidationResult.Failure(
                    ThemeResolveError.ResourceNotRegularFile(relativePath)
                )
            }

            val size = file.length()
            if (size < ThemeResourceLimits.MIN_FONT_FILE_BYTES) {
                return ThemeFontFileValidationResult.Failure(
                    ThemeResolveError.FontFileTooSmall(
                        relativePath = relativePath,
                        actualBytes = size,
                        minimumBytes = ThemeResourceLimits.MIN_FONT_FILE_BYTES
                    )
                )
            }
            if (size > ThemeResourceLimits.MAX_SINGLE_FONT_FILE_BYTES) {
                return ThemeFontFileValidationResult.Failure(
                    ThemeResolveError.FontTooLarge(
                        relativePath = relativePath,
                        actualBytes = size,
                        maximumBytes = ThemeResourceLimits.MAX_SINGLE_FONT_FILE_BYTES
                    )
                )
            }
            files[slot] = ValidatedThemeFontFile(
                slot = slot,
                relativePath = relativePath,
                file = file,
                size = size,
                lastModified = file.lastModified()
            )
        }

        val totalSize = files.values.sumOf { it.size }
        if (totalSize > ThemeResourceLimits.MAX_TOTAL_FONT_FILE_BYTES) {
            return ThemeFontFileValidationResult.Failure(
                ThemeResolveError.FontTotalTooLarge(
                    actualBytes = totalSize,
                    maximumBytes = ThemeResourceLimits.MAX_TOTAL_FONT_FILE_BYTES
                )
            )
        }

        files.values.forEach { font ->
            validateSfnt(font)?.let { error ->
                return ThemeFontFileValidationResult.Failure(error)
            }
        }
        return ThemeFontFileValidationResult.Success(files)
    }

    private fun validateSfnt(font: ValidatedThemeFontFile): ThemeResolveError? {
        return try {
            RandomAccessFile(font.file, "r").use { input ->
                val signature = input.readInt()
                when (signature) {
                    TRUE_TYPE_SFNT, APPLE_TRUE_TYPE_SFNT -> Unit
                    OPEN_TYPE_CFF, TRUE_TYPE_COLLECTION, WOFF, WOFF2 ->
                        return ThemeResolveError.UnsupportedFontFormat(font.relativePath)
                    else -> {
                        return ThemeResolveError.FontHeaderInvalid(
                            font.relativePath,
                            "Unsupported SFNT signature 0x${signature.toUInt().toString(16)}"
                        )
                    }
                }

                val tableCount = input.readUnsignedShort()
                input.skipBytes(6)
                if (tableCount !in 1..MAX_TABLE_COUNT) {
                    return ThemeResolveError.FontHeaderInvalid(
                        font.relativePath,
                        "Invalid SFNT table count $tableCount"
                    )
                }
                val directoryEnd = 12L + tableCount.toLong() * 16L
                if (directoryEnd > font.size) {
                    return ThemeResolveError.FontHeaderInvalid(
                        font.relativePath,
                        "SFNT table directory exceeds file bounds"
                    )
                }

                val tags = mutableSetOf<String>()
                repeat(tableCount) {
                    val tagBytes = ByteArray(4)
                    input.readFully(tagBytes)
                    val tag = tagBytes.toString(Charsets.ISO_8859_1)
                    tags += tag
                    input.skipBytes(4)
                    val offset = input.readInt().toUInt().toLong()
                    val length = input.readInt().toUInt().toLong()
                    if (offset > font.size || length > font.size - offset) {
                        return ThemeResolveError.FontHeaderInvalid(
                            font.relativePath,
                            "SFNT table $tag exceeds file bounds"
                        )
                    }
                }

                val missingTables = requiredTables - tags
                if (missingTables.isNotEmpty()) {
                    ThemeResolveError.FontHeaderInvalid(
                        font.relativePath,
                        "Missing required SFNT tables: ${missingTables.sorted().joinToString()}"
                    )
                } else {
                    null
                }
            }
        } catch (exception: Exception) {
            ThemeResolveError.ResourceAccessFailed(
                relativePath = font.relativePath,
                reason = exception.message ?: exception::class.java.simpleName
            )
        }
    }
}

internal fun interface ThemeTypefaceFactory {
    fun create(file: File): Typeface
}

internal object PlatformThemeTypefaceFactory : ThemeTypefaceFactory {
    override fun create(file: File): Typeface =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            createWithExplicitSystemFallback(file)
        } else {
            // API 26-28 has no public custom fallback-chain builder. Canvas and
            // Compose use the same Typeface and rely on Android's shaping fallback.
            Typeface.createFromFile(file)
        }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun createWithExplicitSystemFallback(file: File): Typeface {
        val font = android.graphics.fonts.Font.Builder(file).build()
        val family = android.graphics.fonts.FontFamily.Builder(font).build()
        return Typeface.CustomFallbackBuilder(family)
            .setSystemFallback("sans-serif")
            .build()
    }
}

internal sealed interface ThemeFontLoadResult {
    data class Success(
        val fontA: ResolvedFontSlot?,
        val fontB: ResolvedFontSlot?
    ) : ThemeFontLoadResult

    data class Failure(
        val error: ThemeResolveError
    ) : ThemeFontLoadResult
}

internal class ThemeFontLoader(
    private val resources: ThemeResourceProvider,
    private val themeId: String,
    private val themeVersion: String,
    private val themeGeneration: Long,
    private val typefaceFactory: ThemeTypefaceFactory = PlatformThemeTypefaceFactory
) {
    private val cache = mutableMapOf<ThemeFontCacheKey, ResolvedFontSlot>()

    fun load(fonts: ThemeFontManifest): ThemeFontLoadResult {
        val validation = ThemeFontFileValidator.validateDeclaredFiles(fonts, resources)
        if (validation is ThemeFontFileValidationResult.Failure) {
            return ThemeFontLoadResult.Failure(validation.error)
        }
        val files = (validation as ThemeFontFileValidationResult.Success).files
        val fontA = files[FontSlot.A]?.let { loadSlot(it) }
        if (fontA is SlotLoadResult.Failure) {
            return ThemeFontLoadResult.Failure(fontA.error)
        }
        val fontB = files[FontSlot.B]?.let { loadSlot(it) }
        if (fontB is SlotLoadResult.Failure) {
            return ThemeFontLoadResult.Failure(fontB.error)
        }
        return ThemeFontLoadResult.Success(
            fontA = (fontA as? SlotLoadResult.Success)?.slot,
            fontB = (fontB as? SlotLoadResult.Success)?.slot
        )
    }

    private fun loadSlot(font: ValidatedThemeFontFile): SlotLoadResult {
        val key = ThemeFontCacheKey(
            themeId = themeId,
            themeVersion = themeVersion,
            themeGeneration = themeGeneration,
            slot = font.slot,
            relativePath = font.relativePath,
            fileSize = font.size,
            lastModified = font.lastModified
        )
        cache[key]?.let { return SlotLoadResult.Success(it) }

        val typeface = try {
            typefaceFactory.create(font.file)
        } catch (exception: Exception) {
            return SlotLoadResult.Failure(
                ThemeResolveError.FontLoadFailed(
                    relativePath = font.relativePath,
                    reason = exception.message ?: exception::class.java.simpleName
                )
            )
        }
        val slot = ResolvedFontSlot(
            composeFontFamily = FontFamily(typeface),
            androidTypeface = typeface,
            source = FontSource.ThemeFile(key)
        )
        cache[key] = slot
        return SlotLoadResult.Success(slot)
    }

    private sealed interface SlotLoadResult {
        data class Success(val slot: ResolvedFontSlot) : SlotLoadResult
        data class Failure(val error: ThemeResolveError) : SlotLoadResult
    }
}
