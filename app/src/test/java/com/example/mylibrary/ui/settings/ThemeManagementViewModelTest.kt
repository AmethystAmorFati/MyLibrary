package com.example.mylibrary.ui.settings

import com.example.mylibrary.ui.theme.DefaultResolvedTheme
import com.example.mylibrary.ui.theme.DefaultThemeManifest
import com.example.mylibrary.ui.theme.ResolvedTheme
import com.example.mylibrary.ui.theme.ThemeApplyError
import com.example.mylibrary.ui.theme.ThemeApplyResult
import com.example.mylibrary.ui.theme.ThemeManifest
import com.example.mylibrary.ui.theme.ThemeRepository
import com.example.mylibrary.ui.theme.ThemeResolveError
import com.example.mylibrary.ui.theme.importer.InstalledTheme
import com.example.mylibrary.ui.theme.importer.InstalledThemeCatalog
import com.example.mylibrary.ui.theme.importer.InstalledThemeMetadata
import com.example.mylibrary.ui.theme.importer.InstalledThemeStatus
import com.example.mylibrary.ui.theme.importer.ThemeDeleteError
import com.example.mylibrary.ui.theme.importer.ThemeDeleteResult
import com.example.mylibrary.ui.theme.importer.ThemePackageCopyResult
import com.example.mylibrary.ui.theme.importer.ThemePackageError
import com.example.mylibrary.ui.theme.importer.ThemePackageImportResult
import com.example.mylibrary.ui.theme.importer.ThemePackageImportService
import com.example.mylibrary.ui.theme.importer.ThemePackageResult
import com.example.mylibrary.ui.theme.importer.ThemePackageSource
import com.example.mylibrary.ui.theme.importer.ThemePackageSourceFactory
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeManagementViewModelTest {
    @get:Rule
    val mainDispatcherRule = ThemeMainDispatcherRule()

    @Test
    fun defaultThemeIsAlwaysFirstAndInstalledOrderIsPreserved() {
        val result = buildThemeList(
            installed = listOf(
                metadata("current.theme", "Current"),
                metadata("alpha.theme", "Alpha")
            ),
            currentThemeId = "current.theme"
        )

        assertTrue(result.first().isDefault)
        assertTrue(result[1].isCurrent)
        assertEquals(
            listOf(null, "current.theme", "alpha.theme"),
            result.map { it.id }
        )
    }

    @Test
    fun deletingCurrentThemeSwitchesDefaultBeforeDeleting() =
        runTest(mainDispatcherRule.testDispatcher) {
            val events = mutableListOf<String>()
            val repository = FakeRepository(
                initialId = "current.theme",
                events = events
            )
            val catalog = FakeCatalog(events).apply {
                items += metadata("current.theme", "Current")
            }
            val viewModel = viewModel(repository, catalog)
            advanceUntilIdle()

            viewModel.deleteTheme("current.theme")
            advanceUntilIdle()

            assertEquals(
                listOf("default", "delete:current.theme"),
                events
            )
            assertNull(repository.currentThemeId.value)
            assertTrue(catalog.items.isEmpty())
        }

    @Test
    fun failedDefaultSwitchNeverDeletesTheCurrentTheme() =
        runTest(mainDispatcherRule.testDispatcher) {
            val events = mutableListOf<String>()
            val repository = FakeRepository(
                initialId = "current.theme",
                events = events
            ).apply {
                failDefault = true
            }
            val catalog = FakeCatalog(events).apply {
                items += metadata("current.theme", "Current")
            }
            val viewModel = viewModel(repository, catalog)
            advanceUntilIdle()

            viewModel.deleteTheme("current.theme")
            advanceUntilIdle()

            assertEquals(listOf("default"), events)
            assertEquals("current.theme", repository.currentThemeId.value)
            assertEquals(1, catalog.items.size)
        }

    @Test
    fun deleteFailureLeavesTheThemeDiscoverable() =
        runTest(mainDispatcherRule.testDispatcher) {
            val repository = FakeRepository()
            val catalog = FakeCatalog().apply {
                items += metadata("undeleted.theme", "Undeleted")
                deleteFailure = true
            }
            val viewModel = viewModel(repository, catalog)
            advanceUntilIdle()

            viewModel.deleteTheme("undeleted.theme")
            advanceUntilIdle()

            assertTrue(
                viewModel.uiState.value.themes.any {
                    it.id == "undeleted.theme"
                }
            )
            assertEquals(
                "无法删除主题文件",
                viewModel.uiState.value.message?.text
            )
        }

    // ---------- Import does not auto-apply ----------

    /**
     * Test 7: 导入两个不同 id 的主题后两者同时存在.
     */
    @Test
    fun importingTwoDifferentIdThemesKeepsBothInList() =
        runTest(mainDispatcherRule.testDispatcher) {
            val repository = FakeRepository()
            val catalog = FakeCatalog()
            val viewModel = viewModel(
                repository = repository,
                catalog = catalog,
                peekManifestResult = ThemePackageResult.Success(
                    DefaultThemeManifest.copy(
                        id = "theme.alpha",
                        name = "Alpha",
                        version = "1"
                    )
                ),
                importResult = ThemePackageImportResult.Installed(
                    installed("theme.alpha", 1L)
                )
            )
            advanceUntilIdle()

            // Import first theme
            viewModel.importTheme(DummySource)
            advanceUntilIdle()

            // Simulate the catalog now containing the first theme
            catalog.items += metadata("theme.alpha", "Alpha")

            // Import second theme with a different id
            val viewModel2 = viewModel(
                repository = repository,
                catalog = catalog,
                peekManifestResult = ThemePackageResult.Success(
                    DefaultThemeManifest.copy(
                        id = "theme.beta",
                        name = "Beta",
                        version = "1"
                    )
                ),
                importResult = ThemePackageImportResult.Installed(
                    installed("theme.beta", 2L)
                )
            )
            advanceUntilIdle()
            viewModel2.importTheme(DummySource)
            advanceUntilIdle()

            catalog.items += metadata("theme.beta", "Beta")
            viewModel2.refresh()
            advanceUntilIdle()

            val themeIds = viewModel2.uiState.value.themes
                .mapNotNull { it.id }
            assertTrue("theme.alpha" in themeIds)
            assertTrue("theme.beta" in themeIds)
        }

    /**
     * Test 8: 导入第二个主题不修改 currentThemeId.
     */
    @Test
    fun importingSecondThemeDoesNotChangeCurrentThemeId() =
        runTest(mainDispatcherRule.testDispatcher) {
            val events = mutableListOf<String>()
            val repository = FakeRepository(
                initialId = "first.theme",
                events = events
            )
            val catalog = FakeCatalog(events).apply {
                items += metadata("first.theme", "First")
            }
            val viewModel = viewModel(
                repository = repository,
                catalog = catalog,
                peekManifestResult = ThemePackageResult.Success(
                    DefaultThemeManifest.copy(
                        id = "second.theme",
                        name = "Second",
                        version = "1"
                    )
                ),
                importResult = ThemePackageImportResult.Installed(
                    installed("second.theme", 2L)
                )
            )
            advanceUntilIdle()

            assertEquals("first.theme", repository.currentThemeId.value)

            viewModel.importTheme(DummySource)
            advanceUntilIdle()

            // currentThemeId must remain "first.theme", not change.
            assertEquals("first.theme", repository.currentThemeId.value)
            // No apply events should have been fired.
            assertTrue(events.none { it.startsWith("apply") })
        }

    /**
     * Test 9: 用户点击应用后才修改 currentThemeId.
     */
    @Test
    fun applyingThemeChangesCurrentThemeId() =
        runTest(mainDispatcherRule.testDispatcher) {
            val events = mutableListOf<String>()
            val repository = FakeRepository(events = events)
            val catalog = FakeCatalog(events).apply {
                items += metadata("target.theme", "Target")
            }
            val viewModel = viewModel(repository, catalog)
            advanceUntilIdle()

            assertNull(repository.currentThemeId.value)

            viewModel.applyTheme("target.theme")
            advanceUntilIdle()

            assertEquals("target.theme", repository.currentThemeId.value)
            assertTrue(events.contains("apply:target.theme"))
            assertEquals(
                "主题已应用",
                viewModel.uiState.value.message?.text
            )
        }

    /**
     * Test 10: 重启后恢复最后明确应用的主题.
     *
     * The ViewModel's init block collects `currentThemeId` from the
     * repository.  After "restart" (re-creating the ViewModel with a
     * repository that already has a persisted currentThemeId), the UI
     * state must reflect that id.
     */
    @Test
    fun viewModelRestoreReflectsPersistedCurrentThemeId() =
        runTest(mainDispatcherRule.testDispatcher) {
            val repository = FakeRepository(initialId = "persisted.theme")
            val catalog = FakeCatalog().apply {
                items += metadata("persisted.theme", "Persisted")
            }
            val viewModel = viewModel(repository, catalog)
            advanceUntilIdle()

            assertEquals("persisted.theme", viewModel.uiState.value.currentThemeId)
            assertTrue(
                viewModel.uiState.value.themes.any {
                    it.id == "persisted.theme" && it.isCurrent
                }
            )
        }

    // ---------- Same-ID confirmation flow ----------

    /**
     * Test 11: 相同 id 未确认时不替换.
     *
     * When peekManifest returns a manifest whose id already exists in
     * the catalog, the ViewModel sets `pendingReplace` and does NOT call
     * `import()`.
     */
    @Test
    fun importingSameIdShowsPendingReplaceWithoutImporting() =
        runTest(mainDispatcherRule.testDispatcher) {
            val events = mutableListOf<String>()
            val repository = FakeRepository(events = events)
            val catalog = FakeCatalog(events).apply {
                items += metadata("existing.theme", "Existing")
            }
            val viewModel = viewModel(
                repository = repository,
                catalog = catalog,
                peekManifestResult = ThemePackageResult.Success(
                    DefaultThemeManifest.copy(
                        id = "existing.theme",
                        name = "Updated",
                        version = "2"
                    )
                ),
                importResult = ThemePackageImportResult.Installed(
                    installed("existing.theme", 99L)
                )
            )
            advanceUntilIdle()

            viewModel.importTheme(DummySource)
            advanceUntilIdle()

            val pending = viewModel.uiState.value.pendingReplace
            assertNotNull(pending)
            assertEquals("existing.theme", pending!!.themeId)
            assertEquals("Existing", pending.existingName)
            assertEquals("1", pending.existingVersion)
            assertEquals("Updated", pending.importingName)
            assertEquals("2", pending.importingVersion)
            // Import was NOT called — no events recorded.
            assertTrue(events.isEmpty())
            assertNull(repository.currentThemeId.value)
        }

    /**
     * Test 12: 相同 id 确认后原子替换.
     *
     * After the user confirms via `confirmReplaceTheme()`, `import()` is
     * called and the catalog is refreshed.
     */
    @Test
    fun confirmingReplaceTriggersImportAndRefreshes() =
        runTest(mainDispatcherRule.testDispatcher) {
            val events = mutableListOf<String>()
            val repository = FakeRepository(events = events)
            val catalog = FakeCatalog(events).apply {
                items += metadata("existing.theme", "Existing")
            }
            val viewModel = viewModel(
                repository = repository,
                catalog = catalog,
                peekManifestResult = ThemePackageResult.Success(
                    DefaultThemeManifest.copy(
                        id = "existing.theme",
                        name = "Updated",
                        version = "2"
                    )
                ),
                importResult = ThemePackageImportResult.Installed(
                    installed("existing.theme", 42L)
                )
            )
            advanceUntilIdle()

            viewModel.importTheme(DummySource)
            advanceUntilIdle()

            assertNotNull(viewModel.uiState.value.pendingReplace)

            viewModel.confirmReplaceTheme()
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.pendingReplace)
            assertEquals(
                "主题已更新",
                viewModel.uiState.value.message?.text
            )
        }

    /**
     * Test 13: 替换当前主题后继续使用该 id 的新版本.
     *
     * When the replaced theme id is the current theme,
     * `confirmReplaceTheme()` calls `applyInstalledTheme()` to refresh
     * the active theme to the new version.
     */
    @Test
    fun confirmingReplaceCurrentThemeReappliesNewVersion() =
        runTest(mainDispatcherRule.testDispatcher) {
            val events = mutableListOf<String>()
            val repository = FakeRepository(
                initialId = "current.theme",
                events = events
            )
            val catalog = FakeCatalog(events).apply {
                items += metadata("current.theme", "Current")
            }
            val viewModel = viewModel(
                repository = repository,
                catalog = catalog,
                peekManifestResult = ThemePackageResult.Success(
                    DefaultThemeManifest.copy(
                        id = "current.theme",
                        name = "Updated",
                        version = "2"
                    )
                ),
                importResult = ThemePackageImportResult.Installed(
                    installed("current.theme", 55L)
                )
            )
            advanceUntilIdle()

            viewModel.importTheme(DummySource)
            advanceUntilIdle()

            viewModel.confirmReplaceTheme()
            advanceUntilIdle()

            // applyInstalledTheme was called to refresh the current theme.
            assertTrue(events.contains("apply:current.theme"))
            assertEquals("current.theme", repository.currentThemeId.value)
            assertEquals(
                "主题已更新",
                viewModel.uiState.value.message?.text
            )
        }

    /**
     * Test 14: 替换非当前主题不自动切换.
     *
     * When the replaced theme id is NOT the current theme,
     * `confirmReplaceTheme()` does NOT call `applyInstalledTheme()`.
     */
    @Test
    fun confirmingReplaceNonCurrentThemeDoesNotSwitch() =
        runTest(mainDispatcherRule.testDispatcher) {
            val events = mutableListOf<String>()
            val repository = FakeRepository(
                initialId = "active.theme",
                events = events
            )
            val catalog = FakeCatalog(events).apply {
                items += metadata("active.theme", "Active")
                items += metadata("other.theme", "Other")
            }
            val viewModel = viewModel(
                repository = repository,
                catalog = catalog,
                peekManifestResult = ThemePackageResult.Success(
                    DefaultThemeManifest.copy(
                        id = "other.theme",
                        name = "Other Updated",
                        version = "2"
                    )
                ),
                importResult = ThemePackageImportResult.Installed(
                    installed("other.theme", 33L)
                )
            )
            advanceUntilIdle()

            viewModel.importTheme(DummySource)
            advanceUntilIdle()

            viewModel.confirmReplaceTheme()
            advanceUntilIdle()

            // applyInstalledTheme was NOT called for the non-current theme.
            assertTrue(events.none { it.contains("other.theme") })
            assertEquals("active.theme", repository.currentThemeId.value)
            assertEquals(
                "主题已更新",
                viewModel.uiState.value.message?.text
            )
        }

    /**
     * Test 15: 删除一个非当前主题不影响其他主题.
     */
    @Test
    fun deletingNonCurrentThemeKeepsOthers() =
        runTest(mainDispatcherRule.testDispatcher) {
            val repository = FakeRepository(
                initialId = "current.theme"
            )
            val catalog = FakeCatalog().apply {
                items += metadata("current.theme", "Current")
                items += metadata("other.theme", "Other")
                items += metadata("third.theme", "Third")
            }
            val viewModel = viewModel(repository, catalog)
            advanceUntilIdle()

            viewModel.deleteTheme("other.theme")
            advanceUntilIdle()

            assertEquals("current.theme", repository.currentThemeId.value)
            val remainingIds = viewModel.uiState.value.themes
                .mapNotNull { it.id }
            assertTrue("current.theme" in remainingIds)
            assertTrue("third.theme" in remainingIds)
            assertTrue("other.theme" !in remainingIds)
        }

    /**
     * Test 18: 旧 peanutpersimmon.example 主题继续可识别.
     *
     * A theme with the legacy id `peanutpersimmon.example` is treated
     * the same as any other id — it can be imported, listed, and applied
     * without issues.
     */
    @Test
    fun legacyPeanutPersimmonExampleIdIsHandled() =
        runTest(mainDispatcherRule.testDispatcher) {
            val events = mutableListOf<String>()
            val repository = FakeRepository(events = events)
            val catalog = FakeCatalog(events)
            val viewModel = viewModel(
                repository = repository,
                catalog = catalog,
                peekManifestResult = ThemePackageResult.Success(
                    DefaultThemeManifest.copy(
                        id = "peanutpersimmon.example",
                        name = "Legacy",
                        version = "1"
                    )
                ),
                importResult = ThemePackageImportResult.Installed(
                    installed("peanutpersimmon.example", 1L)
                )
            )
            advanceUntilIdle()

            viewModel.importTheme(DummySource)
            advanceUntilIdle()

            // Import succeeds without auto-apply.
            assertTrue(events.isEmpty())
            assertEquals(
                "主题已导入",
                viewModel.uiState.value.message?.text
            )

            // User can explicitly apply the legacy theme.
            catalog.items += metadata("peanutpersimmon.example", "Legacy")
            viewModel.refresh()
            advanceUntilIdle()

            viewModel.applyTheme("peanutpersimmon.example")
            advanceUntilIdle()

            assertEquals(
                "peanutpersimmon.example",
                repository.currentThemeId.value
            )
        }

    /**
     * Test: cancelling a pending replace does not modify any files.
     */
    @Test
    fun cancellingPendingReplaceDoesNotImport() =
        runTest(mainDispatcherRule.testDispatcher) {
            val events = mutableListOf<String>()
            val repository = FakeRepository(events = events)
            val catalog = FakeCatalog(events).apply {
                items += metadata("existing.theme", "Existing")
            }
            val viewModel = viewModel(
                repository = repository,
                catalog = catalog,
                peekManifestResult = ThemePackageResult.Success(
                    DefaultThemeManifest.copy(
                        id = "existing.theme",
                        name = "Updated",
                        version = "2"
                    )
                )
            )
            advanceUntilIdle()

            viewModel.importTheme(DummySource)
            advanceUntilIdle()

            assertNotNull(viewModel.uiState.value.pendingReplace)

            viewModel.cancelReplaceTheme()
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.pendingReplace)
            assertTrue(events.isEmpty())
            // Catalog unchanged.
            assertEquals(1, catalog.items.size)
            assertEquals("existing.theme", catalog.items[0].id)
        }

    @Test
    fun importingNewThemeDoesNotAutoApply() =
        runTest(mainDispatcherRule.testDispatcher) {
            val events = mutableListOf<String>()
            val repository = FakeRepository(events = events)
            val catalog = FakeCatalog(events)
            val installed = installed("new.imported.theme", 7L)
            val viewModel = viewModel(
                repository = repository,
                catalog = catalog,
                importResult = ThemePackageImportResult.Installed(
                    installed
                )
            )
            advanceUntilIdle()

            viewModel.importTheme(DummySource)
            advanceUntilIdle()

            // Import must NOT call applyInstalledTheme or applyDefaultTheme.
            assertTrue(events.isEmpty())
            // currentThemeId remains null (default).
            assertNull(repository.currentThemeId.value)
            assertEquals(
                "主题已导入",
                viewModel.uiState.value.message?.text
            )

            // Simulate the catalog picking up the newly installed theme
            // from disk (the real catalog reads the filesystem).
            catalog.items += metadata("new.imported.theme", "Imported")
            viewModel.refresh()
            advanceUntilIdle()

            // The imported theme now appears in the refreshed list.
            assertTrue(
                viewModel.uiState.value.themes.any {
                    it.id == "new.imported.theme"
                }
            )
            // Still no apply events after refresh.
            assertTrue(events.isEmpty())
        }

    @Test
    fun importedThemeRemainsDiscoverableWithoutAutoApply() =
        runTest(mainDispatcherRule.testDispatcher) {
            val repository = FakeRepository()
            val catalog = FakeCatalog()
            val viewModel = viewModel(
                repository = repository,
                catalog = catalog,
                importResult = ThemePackageImportResult.Installed(
                    installed("new.imported.theme", 8L)
                )
            )
            advanceUntilIdle()

            viewModel.importTheme(DummySource)
            advanceUntilIdle()

            assertNull(repository.currentThemeId.value)
            assertEquals(
                "主题已导入",
                viewModel.uiState.value.message?.text
            )
        }

    private fun viewModel(
        repository: FakeRepository,
        catalog: FakeCatalog,
        importResult: ThemePackageImportResult =
            ThemePackageImportResult.Failure(
                ThemePackageError.NotZipArchive("fixture")
            ),
        peekManifestResult: ThemePackageResult<ThemeManifest> =
            ThemePackageResult.Success(
                DefaultThemeManifest.copy(
                    id = "new.imported.theme",
                    name = "New Imported",
                    version = "1"
                )
            )
    ): ThemeManagementViewModel = ThemeManagementViewModel(
        importer = object : ThemePackageImportService {
            override suspend fun import(
                source: ThemePackageSource
            ): ThemePackageImportResult = importResult

            override suspend fun peekManifest(
                source: ThemePackageSource
            ): ThemePackageResult<ThemeManifest> = peekManifestResult
        },
        catalog = catalog,
        repository = repository,
        sourceFactory = ThemePackageSourceFactory {
            DummySource
        }
    )

    private fun metadata(
        id: String,
        name: String
    ): InstalledThemeMetadata = InstalledThemeMetadata(
        id = id,
        name = name,
        author = null,
        version = "1",
        status = InstalledThemeStatus.VALID
    )

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

    private object DummySource : ThemePackageSource {
        override suspend fun copyTo(
            destination: File
        ): ThemePackageCopyResult =
            ThemePackageCopyResult.Success(0L)
    }

    private class FakeCatalog(
        private val events: MutableList<String> = mutableListOf()
    ) : InstalledThemeCatalog {
        val items = mutableListOf<InstalledThemeMetadata>()
        var deleteFailure = false

        override suspend fun listInstalledThemes(
            currentThemeId: String?
        ): List<InstalledThemeMetadata> = items.toList()

        override suspend fun load(
            themeId: String,
            themeGeneration: Long
        ): ThemePackageResult<InstalledTheme> =
            ThemePackageResult.Failure(
                ThemePackageError.InstallFailed("unused")
            )

        override suspend fun delete(
            themeId: String
        ): ThemeDeleteResult {
            events += "delete:$themeId"
            if (deleteFailure) {
                return ThemeDeleteResult.Failure(
                    ThemeDeleteError.DeleteFailed(themeId)
                )
            }
            items.removeAll { it.id == themeId }
            return ThemeDeleteResult.Success
        }
    }

    private class FakeRepository(
        initialId: String? = null,
        private val events: MutableList<String> = mutableListOf()
    ) : ThemeRepository {
        private val mutableTheme = MutableStateFlow(
            if (initialId == null) {
                DefaultResolvedTheme
            } else {
                themed(initialId)
            }
        )
        private val mutableId = MutableStateFlow(initialId)
        private val mutableRestoreError =
            MutableStateFlow<ThemeResolveError?>(null)
        var failDefault = false
        var failApply = false

        override val currentTheme: StateFlow<ResolvedTheme> = mutableTheme
        override val currentThemeId: StateFlow<String?> = mutableId
        override val lastRestoreError: StateFlow<ThemeResolveError?> =
            mutableRestoreError

        override suspend fun restore() = Unit

        override suspend fun applyInstalledTheme(
            themeId: String
        ): ThemeApplyResult {
            events += "apply:$themeId"
            return apply(themeId, themed(themeId))
        }

        override suspend fun applyInstalledTheme(
            installedTheme: InstalledTheme
        ): ThemeApplyResult {
            events += "apply-installed:${installedTheme.id}"
            return apply(
                installedTheme.id,
                installedTheme.resolvedTheme
            )
        }

        override suspend fun applyDefaultTheme(): ThemeApplyResult {
            events += "default"
            if (failDefault) {
                return ThemeApplyResult.Failure(
                    ThemeApplyError.PreferenceWriteFailed(null)
                )
            }
            mutableId.value = null
            mutableTheme.value = DefaultResolvedTheme
            return ThemeApplyResult.Applied(
                null,
                DefaultResolvedTheme
            )
        }

        override fun acknowledgeRestoreError() {
            mutableRestoreError.value = null
        }

        private fun apply(
            id: String,
            theme: ResolvedTheme
        ): ThemeApplyResult {
            if (failApply) {
                return ThemeApplyResult.Failure(
                    ThemeApplyError.PreferenceWriteFailed(id)
                )
            }
            mutableId.value = id
            mutableTheme.value = theme
            return ThemeApplyResult.Applied(id, theme)
        }

        private companion object {
            fun themed(id: String): ResolvedTheme =
                DefaultResolvedTheme.copy(
                    id = id,
                    name = id,
                    themeGeneration = 1L
                )
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private class ThemeMainDispatcherRule(
    val testDispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
