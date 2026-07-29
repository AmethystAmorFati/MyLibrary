package com.example.mylibrary.ui.theme

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ThemeResourceProviderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun legalRelativePathCanBeResolvedAndRead() {
        val root = temporaryFolder.newFolder("theme")
        val file = File(root, "fonts/test.ttf").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }
        val provider = DirectoryThemeResourceProvider(root)

        assertTrue(provider.exists("fonts/test.ttf"))
        assertTrue(provider.resolveFile("fonts/test.ttf").isFile)
        assertArrayEquals(
            byteArrayOf(1, 2, 3, 4),
            provider.open("fonts/test.ttf").use { it.readBytes() }
        )
        assertTrue(file.canonicalFile == provider.resolveFile("fonts/test.ttf"))
    }

    @Test
    fun parentTraversalIsRejected() {
        val provider = DirectoryThemeResourceProvider(
            temporaryFolder.newFolder("theme")
        )

        assertPathRejected(provider, "../outside.ttf")
        assertPathRejected(provider, "fonts/../outside.ttf")
    }

    @Test
    fun absoluteAndDriveQualifiedPathsAreRejected() {
        val root = temporaryFolder.newFolder("theme")
        val provider = DirectoryThemeResourceProvider(root)
        val absolutePath = File(temporaryFolder.root, "outside.ttf").absolutePath

        assertPathRejected(provider, absolutePath)
        assertPathRejected(provider, "C:/outside.ttf")
    }

    @Test
    fun backslashCannotBypassRelativePathRules() {
        val provider = DirectoryThemeResourceProvider(
            temporaryFolder.newFolder("theme")
        )

        assertPathRejected(provider, "fonts\\outside.ttf")
    }

    @Test
    fun siblingPathOutsideCanonicalRootIsRejected() {
        val provider = DirectoryThemeResourceProvider(
            temporaryFolder.newFolder("theme")
        )

        assertPathRejected(provider, "../theme-sibling/font.ttf")
    }

    @Test
    fun symbolicLinksAreExplicitlyUnsupported() {
        val root = temporaryFolder.newFolder("theme")
        val fonts = File(root, "fonts").apply { mkdirs() }
        val outside = temporaryFolder.newFile("outside.ttf").apply {
            writeText("outside")
        }
        val link = File(fonts, "linked.ttf").toPath()
        try {
            Files.createSymbolicLink(link, outside.toPath())
        } catch (exception: Exception) {
            assumeNoException("This environment cannot create symbolic links", exception)
        }
        val provider = DirectoryThemeResourceProvider(root)

        assertPathRejected(provider, "fonts/linked.ttf")
    }

    private fun assertPathRejected(
        provider: ThemeResourceProvider,
        relativePath: String
    ) {
        val failure = runCatching {
            provider.resolveFile(relativePath)
        }.exceptionOrNull()

        assertTrue(failure is ThemeResourceAccessException)
        assertTrue(
            (failure as ThemeResourceAccessException).error is
                ThemeResolveError.PathEscapesRoot
        )
    }
}
