package com.example.mylibrary.ui.theme

import com.example.mylibrary.data.preferences.ThemePreferenceStore
import com.example.mylibrary.ui.theme.importer.InstalledTheme
import com.example.mylibrary.ui.theme.importer.InstalledThemeCatalog
import com.example.mylibrary.ui.theme.importer.InstalledThemeMetadata
import com.example.mylibrary.ui.theme.importer.ThemeDeleteResult
import com.example.mylibrary.ui.theme.importer.ThemePackageError
import com.example.mylibrary.ui.theme.importer.ThemePackageResult
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeRepositoryManagementTest {
    @Test
    fun missingPersistedIdKeepsImmediateDefault() = runTest {
        val store = FakeThemePreferenceStore()
        val repository = repository(store, FakeCatalog())

        repository.restore()

        assertSame(DefaultResolvedTheme, repository.currentTheme.value)
        assertNull(repository.currentThemeId.value)
        assertNull(repository.lastRestoreError.value)
    }

    @Test
    fun validPersistedIdStrictlyRestoresInstalledTheme() = runTest {
        val store = FakeThemePreferenceStore("restore.valid")
        val catalog = FakeCatalog().apply {
            themes["restore.valid"] = { generation ->
                ThemePackageResult.Success(
                    installed("restore.valid", generation)
                )
            }
        }
        val repository = repository(store, catalog)

        repository.restore()

        assertEquals("restore.valid", repository.currentThemeId.value)
        assertEquals("restore.valid", repository.currentTheme.value.id)
        assertEquals(1L, repository.currentTheme.value.themeGeneration)
    }

    @Test
    fun invalidPersistedIdFallsBackAndClearsPreference() = runTest {
        val store = FakeThemePreferenceStore("restore.invalid")
        val catalog = FakeCatalog().apply {
            themes["restore.invalid"] = {
                ThemePackageResult.Failure(
                    ThemePackageError.NotZipArchive("broken")
                )
            }
        }
        val repository = repository(store, catalog)

        repository.restore()

        assertNull(store.value)
        assertTrue(store.clearCalls == 1)
        assertSame(DefaultResolvedTheme, repository.currentTheme.value)
        assertTrue(repository.lastRestoreError.value != null)
    }

    @Test
    fun strictLoadFailureNeverPublishesAReplacement() = runTest {
        val store = FakeThemePreferenceStore()
        val catalog = FakeCatalog().apply {
            themes["old.theme"] = { generation ->
                ThemePackageResult.Success(
                    installed("old.theme", generation)
                )
            }
            themes["broken.theme"] = {
                ThemePackageResult.Failure(
                    ThemePackageError.ThemeResolutionFailed(
                        ThemeResolveError.UnexpectedFailure("broken")
                    )
                )
            }
        }
        val repository = repository(store, catalog)
        assertTrue(
            repository.applyInstalledTheme("old.theme") is
                ThemeApplyResult.Applied
        )
        val before = repository.currentTheme.value

        val result = repository.applyInstalledTheme("broken.theme")

        assertTrue(result is ThemeApplyResult.Failure)
        assertSame(before, repository.currentTheme.value)
        assertEquals("old.theme", repository.currentThemeId.value)
    }

    @Test
    fun persistenceCompletesBeforeRuntimeThemeIsPublished() = runTest {
        val store = FakeThemePreferenceStore()
        val catalog = FakeCatalog().apply {
            themes["ordered.theme"] = { generation ->
                ThemePackageResult.Success(
                    installed("ordered.theme", generation)
                )
            }
        }
        val repository = repository(store, catalog)
        store.onSet = {
            assertSame(
                DefaultResolvedTheme,
                repository.currentTheme.value
            )
            assertNull(repository.currentThemeId.value)
        }

        val result = repository.applyInstalledTheme("ordered.theme")

        assertTrue(result is ThemeApplyResult.Applied)
        assertEquals("ordered.theme", repository.currentThemeId.value)
    }

    @Test
    fun preferenceWriteFailureKeepsTheOldRuntimeTheme() = runTest {
        val store = FakeThemePreferenceStore().apply {
            failSet = true
        }
        val catalog = FakeCatalog().apply {
            themes["write.failure"] = { generation ->
                ThemePackageResult.Success(
                    installed("write.failure", generation)
                )
            }
        }
        val repository = repository(store, catalog)

        val result = repository.applyInstalledTheme("write.failure")

        assertTrue(result is ThemeApplyResult.Failure)
        assertSame(DefaultResolvedTheme, repository.currentTheme.value)
        assertNull(repository.currentThemeId.value)
    }

    @Test
    fun applyingDefaultClearsPreferenceBeforePublishingDefault() = runTest {
        val store = FakeThemePreferenceStore()
        val catalog = FakeCatalog().apply {
            themes["custom.theme"] = { generation ->
                ThemePackageResult.Success(
                    installed("custom.theme", generation)
                )
            }
        }
        val repository = repository(store, catalog)
        repository.applyInstalledTheme("custom.theme")
        store.onClear = {
            assertEquals(
                "custom.theme",
                repository.currentTheme.value.id
            )
        }

        val result = repository.applyDefaultTheme()

        assertTrue(result is ThemeApplyResult.Applied)
        assertNull(store.value)
        assertNull(repository.currentThemeId.value)
        assertSame(DefaultResolvedTheme, repository.currentTheme.value)
    }

    @Test
    fun applyingTheCurrentThemeAgainDoesNotReloadOrAdvanceGeneration() =
        runTest {
            val store = FakeThemePreferenceStore()
            val catalog = FakeCatalog().apply {
                themes["same.theme"] = { generation ->
                    ThemePackageResult.Success(
                        installed("same.theme", generation)
                    )
                }
            }
            val repository = repository(store, catalog)
            repository.applyInstalledTheme("same.theme")
            val generation =
                repository.currentTheme.value.themeGeneration
            val loads = catalog.loadCalls

            val result = repository.applyInstalledTheme("same.theme")

            assertTrue(result is ThemeApplyResult.AlreadyCurrent)
            assertEquals(loads, catalog.loadCalls)
            assertEquals(
                generation,
                repository.currentTheme.value.themeGeneration
            )
        }

    @Test
    fun sameIdInstalledUpdatePublishesItsNewGeneration() = runTest {
        val store = FakeThemePreferenceStore()
        val catalog = FakeCatalog().apply {
            themes["update.theme"] = { generation ->
                ThemePackageResult.Success(
                    installed("update.theme", generation)
                )
            }
        }
        val repository = repository(store, catalog)
        repository.applyInstalledTheme("update.theme")
        val firstGeneration =
            repository.currentTheme.value.themeGeneration

        val updated = installed("update.theme", 99L)
        val result = repository.applyInstalledTheme(updated)

        assertTrue(result is ThemeApplyResult.Applied)
        assertEquals(99L, repository.currentTheme.value.themeGeneration)
        assertTrue(
            repository.currentTheme.value.themeGeneration >
                firstGeneration
        )
        assertEquals(1, store.setCalls)
    }

    private fun repository(
        store: FakeThemePreferenceStore,
        catalog: FakeCatalog
    ): ThemeRepository {
        var generation = 0L
        return DefaultThemeRepository(
            preferenceStore = store,
            installedThemeCatalog = catalog,
            generationSource = { ++generation },
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    private fun installed(id: String, generation: Long): InstalledTheme =
        InstalledTheme(
            id = id,
            name = id,
            version = "1",
            author = null,
            directory = File("unused/$id"),
            resolvedTheme = DefaultResolvedTheme.copy(
                id = id,
                name = id,
                themeGeneration = generation
            )
        )

    private class FakeThemePreferenceStore(
        var value: String? = null
    ) : ThemePreferenceStore {
        var failSet = false
        var setCalls = 0
        var clearCalls = 0
        var onSet: (() -> Unit)? = null
        var onClear: (() -> Unit)? = null

        override suspend fun readCurrentThemeId(): String? = value

        override suspend fun setCurrentThemeId(themeId: String) {
            onSet?.invoke()
            if (failSet) error("simulated DataStore failure")
            setCalls += 1
            value = themeId
        }

        override suspend fun clearCurrentThemeId() {
            onClear?.invoke()
            clearCalls += 1
            value = null
        }
    }

    private class FakeCatalog : InstalledThemeCatalog {
        val themes = mutableMapOf<
            String,
            (Long) -> ThemePackageResult<InstalledTheme>
            >()
        var loadCalls = 0

        override suspend fun listInstalledThemes(
            currentThemeId: String?
        ): List<InstalledThemeMetadata> = emptyList()

        override suspend fun load(
            themeId: String,
            themeGeneration: Long
        ): ThemePackageResult<InstalledTheme> {
            loadCalls += 1
            return themes[themeId]?.invoke(themeGeneration)
                ?: ThemePackageResult.Failure(
                    ThemePackageError.InstallFailed("missing")
                )
        }

        override suspend fun delete(
            themeId: String
        ): ThemeDeleteResult = ThemeDeleteResult.Success
    }
}
