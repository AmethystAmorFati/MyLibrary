package com.example.mylibrary.ui.theme.importer

import android.util.Log
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

data class ThemeInstallationRecoveryReport(
    val stagingEntriesRemoved: Int,
    val rollbackThemesRestored: Int,
    val staleRollbacksRemoved: Int
)

class ThemeInstaller(
    rootDirectory: File,
    private val installedThemeLoader: InstalledThemeDirectoryLoader =
        InstalledThemeLoader()
) {
    private val root = rootDirectory.absoluteFile
    val installedDirectory = File(root, "installed")
    val stagingDirectory = File(root, ".staging")
    val rollbackDirectory = File(root, ".rollback")

    fun prepareStagingDirectory(): ThemePackageResult<File> =
        try {
            ensureRoots()
            val staging = File(
                stagingDirectory,
                "import-${UUID.randomUUID()}"
            )
            ThemePackageFileOps.requireChild(stagingDirectory, staging)
            if (!staging.mkdirs()) {
                failThemePackage(
                    ThemePackageError.InstallFailed(
                        "Unable to create a staging directory"
                    )
                )
            }
            ThemePackageResult.Success(staging.canonicalFile)
        } catch (failure: ThemePackageFailureException) {
            ThemePackageResult.Failure(failure.error)
        } catch (exception: Exception) {
            ThemePackageResult.Failure(
                ThemePackageError.InstallFailed(
                    exception.message ?: exception::class.java.simpleName
                )
            )
        }

    suspend fun install(
        staging: File,
        themeId: String,
        themeGeneration: Long
    ): ThemePackageResult<InstalledTheme> {
        var oldMovedToRollback = false
        var newMovedToInstalled = false
        val target = File(installedDirectory, themeId)
        val rollback = File(rollbackDirectory, themeId)
        return try {
            ensureRoots()
            val checkedStaging = ThemePackageFileOps.requireChild(
                stagingDirectory,
                staging
            )
            val checkedTarget = ThemePackageFileOps.requireChild(
                installedDirectory,
                target
            )
            val checkedRollback = ThemePackageFileOps.requireChild(
                rollbackDirectory,
                rollback
            )
            if (!checkedStaging.isDirectory) {
                failThemePackage(
                    ThemePackageError.InstallFailed(
                        "Validated staging directory no longer exists"
                    )
                )
            }
            if (checkedRollback.exists()) {
                failThemePackage(
                    ThemePackageError.InstallFailed(
                        "A rollback directory already exists for $themeId"
                    )
                )
            }
            if (checkedTarget.exists() && !checkedTarget.isDirectory) {
                failThemePackage(
                    ThemePackageError.InstallFailed(
                        "Installed theme target is not a directory"
                    )
                )
            }

            if (checkedTarget.exists()) {
                moveDirectory(checkedTarget, checkedRollback)
                oldMovedToRollback = true
            }
            moveDirectory(checkedStaging, checkedTarget)
            newMovedToInstalled = true

            val installed = when (
                val loaded = installedThemeLoader.load(
                    installedDirectory = checkedTarget,
                    themeGeneration = themeGeneration
                )
            ) {
                is ThemePackageResult.Success -> loaded.value
                is ThemePackageResult.Failure -> {
                    rollbackFailedInstallation(
                        target = checkedTarget,
                        rollback = checkedRollback,
                        oldMovedToRollback = oldMovedToRollback
                    )
                    return loaded
                }
            }
            if (
                checkedRollback.exists() &&
                !ThemePackageFileOps.deleteTree(
                    checkedRollback,
                    rollbackDirectory
                )
            ) {
                // The new installation is already complete. Recovery removes
                // this stale rollback on the next importer entry.
            }
            ThemePackageResult.Success(installed)
        } catch (failure: ThemePackageFailureException) {
            val rollbackFailure = if (
                oldMovedToRollback || newMovedToInstalled
            ) {
                runCatching {
                    rollbackFailedInstallation(
                        target = target,
                        rollback = rollback,
                        oldMovedToRollback = oldMovedToRollback
                    )
                }.exceptionOrNull()
            } else {
                null
            }
            if (rollbackFailure != null) {
                ThemePackageResult.Failure(
                    ThemePackageError.RollbackFailed(
                        rollbackFailure.message
                            ?: rollbackFailure::class.java.simpleName
                    )
                )
            } else {
                ThemePackageResult.Failure(failure.error)
            }
        } catch (exception: Exception) {
            val rollbackFailure = if (
                oldMovedToRollback || newMovedToInstalled
            ) {
                runCatching {
                    rollbackFailedInstallation(
                        target = target,
                        rollback = rollback,
                        oldMovedToRollback = oldMovedToRollback
                    )
                }.exceptionOrNull()
            } else {
                null
            }
            ThemePackageResult.Failure(
                if (rollbackFailure != null) {
                    ThemePackageError.RollbackFailed(
                        rollbackFailure.message
                            ?: rollbackFailure::class.java.simpleName
                    )
                } else {
                    ThemePackageError.InstallFailed(
                        exception.message ?: exception::class.java.simpleName
                    )
                }
            )
        }
    }

    fun recoverInterruptedOperations():
        ThemePackageResult<ThemeInstallationRecoveryReport> =
        try {
            ensureRoots()
            val stagingChildren = stagingDirectory.listFiles().orEmpty()
            stagingChildren.forEach { child ->
                if (!ThemePackageFileOps.deleteTree(child, stagingDirectory)) {
                    failThemePackage(
                        ThemePackageError.RecoveryFailed(
                            child.path,
                            "Unable to remove stale staging data"
                        )
                    )
                }
            }

            var restored = 0
            var removed = 0
            rollbackDirectory.listFiles().orEmpty().forEach { rollback ->
                val id = rollback.name
                if (
                    !THEME_ID_PATTERN.matches(id) ||
                    !rollback.isDirectory ||
                    Files.isSymbolicLink(rollback.toPath())
                ) {
                    failThemePackage(
                        ThemePackageError.RecoveryFailed(
                            rollback.path,
                            "Rollback entry cannot be identified safely"
                        )
                    )
                }
                val installed = File(installedDirectory, id)
                ThemePackageFileOps.requireChild(installedDirectory, installed)
                when {
                    installed.exists() && installed.isDirectory &&
                        !Files.isSymbolicLink(installed.toPath()) -> {
                        if (
                            !ThemePackageFileOps.deleteTree(
                                rollback,
                                rollbackDirectory
                            )
                        ) {
                            failThemePackage(
                                ThemePackageError.RecoveryFailed(
                                    rollback.path,
                                    "Unable to remove a stale rollback"
                                )
                            )
                        }
                        removed += 1
                    }

                    installed.exists() -> {
                        failThemePackage(
                            ThemePackageError.RecoveryFailed(
                                installed.path,
                                "Existing installed target is ambiguous"
                            )
                        )
                    }

                    else -> {
                        moveDirectory(rollback, installed)
                        restored += 1
                    }
                }
            }
            ThemePackageResult.Success(
                ThemeInstallationRecoveryReport(
                    stagingEntriesRemoved = stagingChildren.size,
                    rollbackThemesRestored = restored,
                    staleRollbacksRemoved = removed
                )
            )
        } catch (failure: ThemePackageFailureException) {
            ThemePackageResult.Failure(failure.error)
        } catch (exception: Exception) {
            ThemePackageResult.Failure(
                ThemePackageError.RecoveryFailed(
                    root.path,
                    exception.message ?: exception::class.java.simpleName
                )
            )
        }

    private fun rollbackFailedInstallation(
        target: File,
        rollback: File,
        oldMovedToRollback: Boolean
    ) {
        if (
            target.exists() &&
            !ThemePackageFileOps.deleteTree(target, installedDirectory)
        ) {
            failThemePackage(
                ThemePackageError.RollbackFailed(
                    "Unable to remove the failed new installation"
                )
            )
        }
        if (oldMovedToRollback) {
            if (!rollback.isDirectory) {
                failThemePackage(
                    ThemePackageError.RollbackFailed(
                        "The old theme rollback directory is missing"
                    )
                )
            }
            moveDirectory(rollback, target)
        }
    }

    private fun ensureRoots() {
        listOf(root, installedDirectory, stagingDirectory, rollbackDirectory)
            .forEach { directory ->
                if (
                    (!directory.exists() && !directory.mkdirs()) ||
                    !directory.isDirectory ||
                    Files.isSymbolicLink(directory.toPath())
                ) {
                    failThemePackage(
                        ThemePackageError.InstallFailed(
                            "Theme storage directory is unavailable: ${directory.name}"
                        )
                    )
                }
            }
        // All theme subdirectories are children of [root], so they are
        // guaranteed to share the same file system.  The cross-store check
        // below is a defence-in-depth guard, not a correctness requirement.
        // Files.getFileStore() can throw FileSystemException on certain
        // Android OEM file-system implementations (F2FS, exFAT overlays,
        // scoped-storage shims).  When that happens we skip the check
        // instead of failing the entire import, because the guarantee
        // already holds by construction.
        try {
            val rootStore = Files.getFileStore(root.toPath())
            val rootStoreIdentity = rootStore.name() to rootStore.type()
            listOf(installedDirectory, stagingDirectory, rollbackDirectory)
                .forEach { directory ->
                    val store = Files.getFileStore(directory.toPath())
                    if ((store.name() to store.type()) != rootStoreIdentity) {
                        failThemePackage(
                            ThemePackageError.InstallFailed(
                                "Theme directories must share one file system"
                            )
                        )
                    }
                }
        } catch (exception: Exception) {
            Log.w(
                TAG,
                "getFileStore() unavailable on this device; " +
                    "skipping cross-store check: ${exception.message}",
                exception
            )
        }
    }

    private fun moveDirectory(source: File, destination: File) {
        destination.parentFile?.mkdirs()
        try {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), destination.toPath())
        }
    }

    private companion object {
        const val TAG = "ThemeInstaller"
        val THEME_ID_PATTERN = Regex("^[a-z0-9][a-z0-9._-]*$")
    }
}
