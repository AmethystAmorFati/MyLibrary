package com.example.mylibrary.ui.settings

import com.example.mylibrary.backup.model.BackupFailureReason
import com.example.mylibrary.backup.model.BackupResult
import com.example.mylibrary.backup.model.BackupWarning
import com.example.mylibrary.backup.model.ImportRecoveryReport
import com.example.mylibrary.backup.model.RecoveryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportResultMessageTest {
    @Test
    fun fullyRecoveredFailureUsesAccurateRecoveryMessage() {
        val message = importResultMessage(
            BackupResult.Failure(
                reason = BackupFailureReason.DATABASE_ERROR,
                recovery = recovered
            )
        )

        assertEquals("导入失败，原数据已恢复", message)
        assertFalse(message!!.contains("未被修改"))
    }

    @Test
    fun partialRecoveryAsksUserToUseRecentBackup() {
        val message = importResultMessage(
            BackupResult.Failure(
                reason = BackupFailureReason.IO_ERROR,
                recovery = recovered.copy(
                    covers = RecoveryState.FAILED,
                    requiresRecentBackup = true
                )
            )
        )

        assertEquals(
            "导入失败，已尝试恢复，但部分内容可能未完全恢复；请重新导入最近备份",
            message
        )
    }

    @Test
    fun failureWithoutRecoveryReportMakesNoAtomicityPromise() {
        val message = importResultMessage(
            BackupResult.Failure(BackupFailureReason.INVALID_ARCHIVE)
        )

        assertEquals(
            "导入失败；无法确认所有内容均未修改，请检查最近备份",
            message
        )
    }

    @Test
    fun successWithNoWarningsShowsSimpleCompletionMessage() {
        assertEquals(
            "数据导入完成",
            importResultMessage(BackupResult.Success())
        )
    }

    @Test
    fun successWithStagingCleanupWarningShowsCleanupFailure() {
        assertEquals(
            "数据导入完成，但临时文件清理失败",
            importResultMessage(
                BackupResult.Success(
                    warnings = listOf(BackupWarning.StagingCleanupFailed)
                )
            )
        )
    }

    @Test
    fun successWithOldCoverCleanupWarningShowsCoverFailure() {
        assertEquals(
            "数据导入完成，但部分旧封面文件未能清理",
            importResultMessage(
                BackupResult.Success(
                    warnings = listOf(BackupWarning.OldCoverCleanupFailed)
                )
            )
        )
    }

    @Test
    fun successWithSkippedThemesShowsThemeCount() {
        assertEquals(
            "数据导入完成，但2 个主题未能恢复",
            importResultMessage(
                BackupResult.Success(
                    warnings = listOf(BackupWarning.SkippedThemes(2))
                )
            )
        )
    }

    @Test
    fun successWithCurrentThemeUnavailableShowsDefaultFallback() {
        assertEquals(
            "数据导入完成，但当前主题无法恢复，已使用默认主题",
            importResultMessage(
                BackupResult.Success(
                    warnings = listOf(BackupWarning.CurrentThemeUnavailable)
                )
            )
        )
    }

    @Test
    fun successWithThemeRestoreFailedShowsFailureMessage() {
        val message = importResultMessage(
            BackupResult.Success(
                warnings = listOf(BackupWarning.ThemeRestoreFailed)
            )
        )
        assertEquals("数据导入完成，但主题恢复失败", message)
        // Must not look like a complete success
        assertFalse(message == "数据导入完成")
    }

    @Test
    fun successWithThemeRestoreFailedAndOtherWarningsCombinesMessages() {
        val message = importResultMessage(
            BackupResult.Success(
                warnings = listOf(
                    BackupWarning.SkippedThemes(1),
                    BackupWarning.ThemeRestoreFailed,
                    BackupWarning.OldCoverCleanupFailed
                )
            )
        )
        assertTrue(message!!.contains("1 个主题未能恢复"))
        assertTrue(message.contains("主题恢复失败"))
        assertTrue(message.contains("旧封面文件未能清理"))
        assertFalse(message == "数据导入完成")
    }

    @Test
    fun successWithMultipleThemeWarningsCombinesMessages() {
        val message = importResultMessage(
            BackupResult.Success(
                warnings = listOf(
                    BackupWarning.SkippedThemes(1),
                    BackupWarning.CurrentThemeUnavailable
                )
            )
        )
        assertEquals(
            "数据导入完成，但1 个主题未能恢复；当前主题无法恢复，已使用默认主题",
            message
        )
    }

    @Test
    fun successWithThemeAndCoverWarningsCombinesAllNotes() {
        val message = importResultMessage(
            BackupResult.Success(
                warnings = listOf(
                    BackupWarning.SkippedThemes(3),
                    BackupWarning.CurrentThemeUnavailable,
                    BackupWarning.OldCoverCleanupFailed
                )
            )
        )
        assertTrue(message!!.contains("3 个主题未能恢复"))
        assertTrue(message.contains("当前主题无法恢复"))
        assertTrue(message.contains("旧封面文件未能清理"))
    }

    @Test
    fun cancelledImportReturnsNullMessage() {
        assertNull(importResultMessage(BackupResult.Cancelled))
    }

    // --- Export result message tests ---

    @Test
    fun exportSuccessWithNoWarningsShowsSimpleMessage() {
        assertEquals(
            "数据已导出",
            exportResultMessage(BackupResult.Success())
        )
    }

    @Test
    fun exportSuccessWithMissingCoversShowsCount() {
        assertEquals(
            "数据已导出，2 张缺失封面未包含在备份中",
            exportResultMessage(
                BackupResult.Success(
                    warnings = listOf(BackupWarning.MissingCovers(2))
                )
            )
        )
    }

    @Test
    fun exportSuccessWithSkippedThemesShowsCount() {
        assertEquals(
            "数据已导出，1 个损坏主题已跳过",
            exportResultMessage(
                BackupResult.Success(
                    warnings = listOf(BackupWarning.SkippedThemes(1))
                )
            )
        )
    }

    @Test
    fun exportSuccessWithBothWarningsCombinesMessages() {
        val message = exportResultMessage(
            BackupResult.Success(
                warnings = listOf(
                    BackupWarning.MissingCovers(3),
                    BackupWarning.SkippedThemes(2)
                )
            )
        )
        assertEquals(
            "数据已导出，3 张缺失封面未包含在备份中，2 个损坏主题已跳过",
            message
        )
    }

    @Test
    fun exportFailureShowsGenericMessage() {
        assertEquals(
            "导出失败",
            exportResultMessage(
                BackupResult.Failure(BackupFailureReason.IO_ERROR)
            )
        )
    }

    @Test
    fun exportCancelledReturnsNull() {
        assertNull(exportResultMessage(BackupResult.Cancelled))
    }

    private companion object {
        val recovered = ImportRecoveryReport(
            database = RecoveryState.PRESERVED,
            preferences = RecoveryState.RESTORED,
            covers = RecoveryState.RESTORED,
            staging = RecoveryState.RESTORED,
            requiresRecentBackup = false
        )
    }
}
