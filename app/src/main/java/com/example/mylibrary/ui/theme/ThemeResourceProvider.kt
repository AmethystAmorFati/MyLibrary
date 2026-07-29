package com.example.mylibrary.ui.theme

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

interface ThemeResourceProvider {
    fun open(relativePath: String): InputStream
    fun resolveFile(relativePath: String): File
    fun exists(relativePath: String): Boolean
}

class ThemeResourceAccessException(
    val error: ThemeResolveError
) : IOException(error.toString())

/**
 * Read-only access to a resource tree whose root is fixed at construction.
 *
 * Symbolic links are deliberately unsupported, including links that would still
 * resolve inside the root. This keeps the same rule on every supported Android
 * version and removes a path-swap escape route from font loading.
 */
class DirectoryThemeResourceProvider(
    rootDirectory: File
) : ThemeResourceProvider {
    private val canonicalRoot: File
    private val canonicalRootPath: Path

    init {
        val absoluteRoot = rootDirectory.absoluteFile
        require(!Files.isSymbolicLink(absoluteRoot.toPath())) {
            "Theme resource root must not be a symbolic link"
        }
        canonicalRoot = absoluteRoot.canonicalFile
        require(canonicalRoot.isDirectory) {
            "Theme resource root must be an existing directory"
        }
        canonicalRootPath = canonicalRoot.toPath()
    }

    override fun open(relativePath: String): InputStream {
        val file = resolveFile(relativePath)
        if (!Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            throw ThemeResourceAccessException(
                ThemeResolveError.ResourceMissing(relativePath)
            )
        }
        if (!Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            throw ThemeResourceAccessException(
                ThemeResolveError.ResourceNotRegularFile(relativePath)
            )
        }
        return try {
            FileInputStream(file)
        } catch (exception: IOException) {
            throw ThemeResourceAccessException(
                ThemeResolveError.ResourceAccessFailed(
                    relativePath = relativePath,
                    reason = exception.message ?: exception::class.java.simpleName
                )
            )
        }
    }

    override fun resolveFile(relativePath: String): File {
        validateRelativePath(relativePath)
        rejectSymbolicLinkSegments(relativePath)

        val candidate = try {
            File(canonicalRoot, relativePath).canonicalFile
        } catch (exception: IOException) {
            throw ThemeResourceAccessException(
                ThemeResolveError.ResourceAccessFailed(
                    relativePath = relativePath,
                    reason = exception.message ?: exception::class.java.simpleName
                )
            )
        }
        if (!candidate.toPath().startsWith(canonicalRootPath)) {
            throw ThemeResourceAccessException(
                ThemeResolveError.PathEscapesRoot(
                    relativePath = relativePath,
                    reason = "Canonical path is outside the fixed resource root"
                )
            )
        }
        return candidate
    }

    override fun exists(relativePath: String): Boolean {
        val file = resolveFile(relativePath)
        return Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS)
    }

    private fun validateRelativePath(relativePath: String) {
        val segments = relativePath.split('/')
        val invalidReason = when {
            relativePath.isBlank() -> "Path must not be blank"
            relativePath.startsWith('/') -> "Absolute paths are not allowed"
            Regex("^[A-Za-z]:").containsMatchIn(relativePath) ->
                "Drive-qualified paths are not allowed"
            File(relativePath).isAbsolute -> "Absolute paths are not allowed"
            '\\' in relativePath -> "Backslashes are not allowed"
            segments.any { it.isEmpty() || it == "." || it == ".." } ->
                "Dot segments and empty path segments are not allowed"
            ".." in relativePath -> "Path traversal tokens are not allowed"
            else -> null
        }
        if (invalidReason != null) {
            throw ThemeResourceAccessException(
                ThemeResolveError.PathEscapesRoot(relativePath, invalidReason)
            )
        }
    }

    private fun rejectSymbolicLinkSegments(relativePath: String) {
        var candidate = canonicalRootPath
        relativePath.split('/').forEach { segment ->
            candidate = candidate.resolve(segment)
            if (Files.isSymbolicLink(candidate)) {
                throw ThemeResourceAccessException(
                    ThemeResolveError.PathEscapesRoot(
                        relativePath = relativePath,
                        reason = "Symbolic links are not supported in theme resources"
                    )
                )
            }
        }
    }
}
