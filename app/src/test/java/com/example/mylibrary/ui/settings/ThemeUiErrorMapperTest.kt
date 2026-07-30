package com.example.mylibrary.ui.settings

import com.example.mylibrary.ui.theme.ThemeApplyError
import com.example.mylibrary.ui.theme.importer.ThemePackageError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ThemeUiErrorMapperTest {
    @Test
    fun userMessagesNeverExposePathsOrInternalDetails() {
        val secretPath =
            "C:\\private\\themes\\installed\\secret\\manifest.json"
        val messages = listOf(
            ThemeUiErrorMapper.importFailure(
                ThemePackageError.ChecksumMismatch(
                    path = secretPath,
                    expectedSha256 = "0".repeat(64),
                    actualSha256 = "1".repeat(64)
                )
            ),
            ThemeUiErrorMapper.importFailure(
                ThemePackageError.SourceReadFailed(
                    "/data/user/0/private/source"
                )
            ),
            ThemeUiErrorMapper.applyFailure(
                ThemeApplyError.InstalledThemeInvalid(
                    "secret.theme",
                    ThemePackageError.InstallFailed(secretPath)
                )
            )
        )

        messages.forEach { message ->
            assertFalse(message.contains("C:\\"))
            assertFalse(message.contains("/data/"))
            assertFalse(message.contains("ThemePackageError"))
        }
    }

    @Test
    fun packageFailureCategoriesUseFrozenChineseMessages() {
        assertEquals(
            "不是有效的 MyLibrary 主题包",
            ThemeUiErrorMapper.importFailure(
                ThemePackageError.NotZipArchive("fixture")
            )
        )
        assertEquals(
            "主题包超过允许限制",
            ThemeUiErrorMapper.importFailure(
                ThemePackageError.PackageTooLarge(2L, 1L)
            )
        )
        assertEquals(
            "主题已安装，但无法保存当前选择",
            ThemeUiErrorMapper.applyFailure(
                ThemeApplyError.PreferenceWriteFailed("theme.id"),
                installedDuringOperation = true
            )
        )
    }
}
