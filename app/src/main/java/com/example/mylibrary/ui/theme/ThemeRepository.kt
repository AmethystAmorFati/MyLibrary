package com.example.mylibrary.ui.theme

import com.example.mylibrary.data.preferences.ThemePreferenceStore
import com.example.mylibrary.ui.theme.importer.InstalledTheme
import com.example.mylibrary.ui.theme.importer.InstalledThemeCatalog
import com.example.mylibrary.ui.theme.importer.ThemeInstaller
import com.example.mylibrary.ui.theme.importer.ThemePackageError
import com.example.mylibrary.ui.theme.importer.ThemePackageResult
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface ThemeRepository {
    val currentTheme: StateFlow<ResolvedTheme>
    val currentThemeId: StateFlow<String?>
    val lastRestoreError: StateFlow<ThemeResolveError?>

    suspend fun restore()

    suspend fun applyInstalledTheme(themeId: String): ThemeApplyResult

    suspend fun applyInstalledTheme(
        installedTheme: InstalledTheme
    ): ThemeApplyResult

    suspend fun applyDefaultTheme(): ThemeApplyResult

    fun acknowledgeRestoreError()
}

sealed interface ThemeApplyError {
    data class InstalledThemeInvalid(
        val themeId: String,
        val error: ThemePackageError
    ) : ThemeApplyError

    data class PreferenceReadFailed(
        val operation: String
    ) : ThemeApplyError

    data class PreferenceWriteFailed(
        val targetThemeId: String?
    ) : ThemeApplyError
}

sealed interface ThemeApplyResult {
    data class Applied(
        val themeId: String?,
        val theme: ResolvedTheme
    ) : ThemeApplyResult

    data class AlreadyCurrent(
        val themeId: String?,
        val theme: ResolvedTheme
    ) : ThemeApplyResult

    data class Failure(val error: ThemeApplyError) : ThemeApplyResult
}

data class ThemeRestoreRequest(
    val manifest: ThemeManifest,
    val resources: ThemeResourceProvider,
    val themeGeneration: Long
)

class ThemeGenerationSource(
    initialValue: Long = System.nanoTime()
) {
    private val value = AtomicLong(initialValue)

    fun next(): Long = value.updateAndGet { previous ->
        val clock = System.nanoTime()
        if (clock > previous) clock else previous + 1L
    }
}

class DefaultThemeRepository(
    private val restoreRequest: ThemeRestoreRequest? = null,
    private val preferenceStore: ThemePreferenceStore? = null,
    private val installedThemeCatalog: InstalledThemeCatalog? = null,
    private val themeInstaller: ThemeInstaller? = null,
    private val generationSource: () -> Long =
        ThemeGenerationSource()::next,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ThemeRepository {
    private val defaultTheme = DefaultResolvedTheme
    private val operationMutex = Mutex()
    private val mutableCurrentTheme = MutableStateFlow(defaultTheme)
    private val mutableCurrentThemeId = MutableStateFlow<String?>(null)
    private val mutableLastRestoreError =
        MutableStateFlow<ThemeResolveError?>(null)
    private var restoreAttempted = false

    override val currentTheme: StateFlow<ResolvedTheme> =
        mutableCurrentTheme.asStateFlow()
    override val currentThemeId: StateFlow<String?> =
        mutableCurrentThemeId.asStateFlow()
    override val lastRestoreError: StateFlow<ThemeResolveError?> =
        mutableLastRestoreError.asStateFlow()

    override suspend fun restore() {
        operationMutex.withLock {
            if (restoreAttempted) return@withLock
            restoreAttempted = true
            val store = preferenceStore
            val catalog = installedThemeCatalog
            if (store == null || catalog == null) {
                restoreLegacyRequest()
                return@withLock
            }
            val recovery = withContext(ioDispatcher) {
                themeInstaller?.recoverInterruptedOperations()
            }
            if (recovery is ThemePackageResult.Failure) {
                mutableCurrentThemeId.value = null
                mutableCurrentTheme.value = defaultTheme
                mutableLastRestoreError.value =
                    ThemeResolveError.UnexpectedFailure(
                        "Theme storage recovery could not be completed"
                    )
                return@withLock
            }

            val persistedId = try {
                withContext(ioDispatcher) {
                    store.readCurrentThemeId()
                }
            } catch (_: Exception) {
                mutableCurrentThemeId.value = null
                mutableCurrentTheme.value = defaultTheme
                mutableLastRestoreError.value =
                    ThemeResolveError.UnexpectedFailure(
                        "Unable to read the saved theme selection"
                    )
                return@withLock
            }
            if (persistedId == null) {
                mutableCurrentThemeId.value = null
                mutableCurrentTheme.value = defaultTheme
                mutableLastRestoreError.value = null
                return@withLock
            }

            when (
                val loaded = catalog.load(
                    persistedId,
                    generationSource()
                )
            ) {
                is ThemePackageResult.Success -> {
                    mutableCurrentThemeId.value = persistedId
                    mutableCurrentTheme.value =
                        loaded.value.resolvedTheme
                    mutableLastRestoreError.value = null
                }

                is ThemePackageResult.Failure -> {
                    runCatching {
                        withContext(ioDispatcher) {
                            store.clearCurrentThemeId()
                        }
                    }
                    mutableCurrentThemeId.value = null
                    mutableCurrentTheme.value = defaultTheme
                    mutableLastRestoreError.value =
                        loaded.error.toThemeResolveError()
                }
            }
        }
    }

    override suspend fun applyInstalledTheme(
        themeId: String
    ): ThemeApplyResult = operationMutex.withLock {
        if (
            mutableCurrentThemeId.value == themeId &&
            mutableCurrentTheme.value.id == themeId
        ) {
            return@withLock ThemeApplyResult.AlreadyCurrent(
                themeId,
                mutableCurrentTheme.value
            )
        }
        val catalog = installedThemeCatalog
            ?: return@withLock ThemeApplyResult.Failure(
                ThemeApplyError.PreferenceReadFailed(
                    "Installed theme catalog is unavailable"
                )
            )
        when (val loaded = catalog.load(themeId, generationSource())) {
            is ThemePackageResult.Success ->
                persistAndPublish(loaded.value)
            is ThemePackageResult.Failure ->
                ThemeApplyResult.Failure(
                    ThemeApplyError.InstalledThemeInvalid(
                        themeId,
                        loaded.error
                    )
                )
        }
    }

    override suspend fun applyInstalledTheme(
        installedTheme: InstalledTheme
    ): ThemeApplyResult = operationMutex.withLock {
        val current = mutableCurrentTheme.value
        val candidate = if (
            current.id == installedTheme.id &&
            current.themeGeneration ==
            installedTheme.resolvedTheme.themeGeneration
        ) {
            when (
                val reloaded = installedThemeCatalog?.load(
                    installedTheme.id,
                    generationSource()
                )
            ) {
                is ThemePackageResult.Success -> reloaded.value
                is ThemePackageResult.Failure -> {
                    return@withLock ThemeApplyResult.Failure(
                        ThemeApplyError.InstalledThemeInvalid(
                            installedTheme.id,
                            reloaded.error
                        )
                    )
                }

                null -> installedTheme
            }
        } else {
            installedTheme
        }
        persistAndPublish(candidate)
    }

    override suspend fun applyDefaultTheme(): ThemeApplyResult =
        operationMutex.withLock {
        if (
            mutableCurrentThemeId.value == null &&
            mutableCurrentTheme.value == defaultTheme
        ) {
            return@withLock ThemeApplyResult.AlreadyCurrent(
                null,
                defaultTheme
            )
        }
        val store = preferenceStore
        if (store != null) {
            try {
                withContext(ioDispatcher) {
                    store.clearCurrentThemeId()
                }
            } catch (_: Exception) {
                return@withLock ThemeApplyResult.Failure(
                    ThemeApplyError.PreferenceWriteFailed(null)
                )
            }
        }
        mutableLastRestoreError.value = null
        mutableCurrentThemeId.value = null
        mutableCurrentTheme.value = defaultTheme
        ThemeApplyResult.Applied(null, defaultTheme)
    }

    override fun acknowledgeRestoreError() {
        mutableLastRestoreError.value = null
    }

    private suspend fun persistAndPublish(
        installedTheme: InstalledTheme
    ): ThemeApplyResult {
        val store = preferenceStore
        if (store != null && mutableCurrentThemeId.value != installedTheme.id) {
            try {
                withContext(ioDispatcher) {
                    store.setCurrentThemeId(installedTheme.id)
                }
            } catch (_: Exception) {
                return ThemeApplyResult.Failure(
                    ThemeApplyError.PreferenceWriteFailed(
                        installedTheme.id
                    )
                )
            }
        }
        mutableLastRestoreError.value = null
        mutableCurrentThemeId.value = installedTheme.id
        mutableCurrentTheme.value = installedTheme.resolvedTheme
        return ThemeApplyResult.Applied(
            installedTheme.id,
            installedTheme.resolvedTheme
        )
    }

    private suspend fun restoreLegacyRequest() {
        val request = restoreRequest
        if (request == null) {
            mutableLastRestoreError.value = null
            mutableCurrentThemeId.value = null
            mutableCurrentTheme.value = defaultTheme
            return
        }
        val resolution = withContext(ioDispatcher) {
            ThemeResolver.resolveOrDefault(
                manifest = request.manifest,
                resources = request.resources,
                themeGeneration = request.themeGeneration
            )
        }
        mutableLastRestoreError.value = resolution.failure
        mutableCurrentThemeId.value =
            resolution.theme.id.takeUnless {
                resolution.theme == defaultTheme
            }
        mutableCurrentTheme.value = resolution.theme
    }
}

private fun ThemePackageError.toThemeResolveError(): ThemeResolveError =
    when (this) {
        is ThemePackageError.ThemeResolutionFailed -> error
        is ThemePackageError.ThemeValidationFailed ->
            ThemeResolveError.ManifestInvalid(issues)
        else -> ThemeResolveError.UnexpectedFailure(
            "The installed theme could not be restored"
        )
    }
