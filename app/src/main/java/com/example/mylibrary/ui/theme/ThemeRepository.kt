package com.example.mylibrary.ui.theme

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

interface ThemeRepository {
    val currentTheme: StateFlow<ResolvedTheme>
    val lastRestoreError: StateFlow<ThemeResolveError?>

    suspend fun restore()
    suspend fun applyDefaultTheme()
}

data class ThemeRestoreRequest(
    val manifest: ThemeManifest,
    val resources: ThemeResourceProvider,
    val themeGeneration: Long
)

class DefaultThemeRepository(
    private val restoreRequest: ThemeRestoreRequest? = null
) : ThemeRepository {
    private val defaultTheme = DefaultResolvedTheme
    private val mutableCurrentTheme = MutableStateFlow(defaultTheme)
    private val mutableLastRestoreError = MutableStateFlow<ThemeResolveError?>(null)

    override val currentTheme: StateFlow<ResolvedTheme> =
        mutableCurrentTheme.asStateFlow()
    override val lastRestoreError: StateFlow<ThemeResolveError?> =
        mutableLastRestoreError.asStateFlow()

    override suspend fun restore() {
        val request = restoreRequest
        if (request == null) {
            mutableLastRestoreError.value = null
            mutableCurrentTheme.value = defaultTheme
            return
        }

        val resolution = withContext(Dispatchers.IO) {
            ThemeResolver.resolveOrDefault(
                manifest = request.manifest,
                resources = request.resources,
                themeGeneration = request.themeGeneration
            )
        }
        mutableLastRestoreError.value = resolution.failure
        mutableCurrentTheme.value = resolution.theme
    }

    override suspend fun applyDefaultTheme() {
        mutableLastRestoreError.value = null
        mutableCurrentTheme.value = defaultTheme
    }
}
