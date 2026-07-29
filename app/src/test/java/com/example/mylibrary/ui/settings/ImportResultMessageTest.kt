package com.example.mylibrary.ui.settings

import com.example.mylibrary.backup.model.BackupFailureReason
import com.example.mylibrary.backup.model.BackupResult
import com.example.mylibrary.backup.model.ImportRecoveryReport
import com.example.mylibrary.backup.model.RecoveryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
