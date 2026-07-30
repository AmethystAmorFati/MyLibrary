package com.example.mylibrary.ui.settings

import android.net.Uri
import com.example.mylibrary.export.report.ReportExportCoordinator
import com.example.mylibrary.export.report.ReportExportError
import com.example.mylibrary.export.report.ReportExportException
import com.example.mylibrary.export.report.TemporaryReportExport
import com.example.mylibrary.export.report.SavedExportLocation
import com.example.mylibrary.export.report.ExportDestination
import com.example.mylibrary.export.visual.VisualExportThemeSnapshot
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsReportExportControllerTest {
    @Test
    fun directSaveRejectsDuplicateAndPublishesSuccess() = runTest {
        val coordinator = FakeCoordinator()
        val controller = controller(coordinator, directSave = true)

        assertTrue(controller.start(CONFIG, theme()))
        assertFalse(controller.start(CONFIG, theme()))
        advanceUntilIdle()

        assertEquals(1, coordinator.prepareCalls)
        assertEquals(1, coordinator.directSaveCalls)
        assertNull(controller.state.value.operation)
        assertEquals(
            "PDF 已保存到下载：Download/MyLibrary\n" +
                "MyLibrary_Monthly_2026_06.pdf",
            controller.state.value.message
        )
        assertEquals(1, coordinator.deleted.size)
    }

    @Test
    fun failureAndCancellationBothReturnIdleButOnlyFailureHasMessage() = runTest {
        val failed = controller(
            FakeCoordinator(
                prepareFailure = ReportExportException(
                    ReportExportError.NO_DATA,
                    "该月份没有可生成报告的记录"
                )
            ),
            directSave = true
        )
        failed.start(CONFIG, theme())
        advanceUntilIdle()
        assertEquals("该月份没有可生成报告的记录", failed.state.value.message)

        val cancelled = controller(
            FakeCoordinator(prepareFailure = CancellationException("cancel")),
            directSave = true
        )
        cancelled.start(CONFIG, theme())
        advanceUntilIdle()
        assertNull(cancelled.state.value.operation)
        assertNull(cancelled.state.value.message)
    }

    @Test
    fun legacyPdfRequestsSafAndCancelCleansTemporaryOutput() = runTest {
        val coordinator = FakeCoordinator()
        val controller = controller(coordinator, directSave = false)

        controller.start(CONFIG, theme())
        advanceUntilIdle()

        assertEquals(
            ReportSafDestination.PDF_DOCUMENT,
            controller.state.value.safRequest?.destination
        )
        assertTrue(controller.onSafResult(null))
        assertNull(controller.state.value.operation)
        assertNull(controller.state.value.message)
        assertEquals(1, coordinator.deleted.size)
    }

    @Test
    fun safSuccessNamesTheFileWithoutInventingAnAbsolutePath() = runTest {
        val coordinator = FakeCoordinator()
        val controller = controller(coordinator, directSave = false)

        controller.start(CONFIG, theme())
        advanceUntilIdle()
        controller.consumeSafRequest()
        assertTrue(
            controller.onSafResult(
                Uri.parse("content://documents/report.pdf")
            )
        )
        advanceUntilIdle()

        assertEquals(
            "PDF 已保存到你选择的位置\n" +
                "MyLibrary_Monthly_2026_06.pdf",
            controller.state.value.message
        )
        assertFalse(controller.state.value.message.orEmpty().contains("content://"))
        assertFalse(controller.state.value.message.orEmpty().contains("C:\\"))
    }

    private fun TestScope.controller(
        coordinator: FakeCoordinator,
        directSave: Boolean
    ) = SettingsReportExportController(
        coordinator = coordinator,
        scope = this,
        directSaveSupported = directSave
    )

    private fun theme() = VisualExportThemeSnapshot(
        backgroundColor = 0,
        textPrimary = 1,
        textSecondary = 2,
        border = 3,
        placeholderColor = 4,
        headingTypeface = null,
        contentTypeface = null
    )

    private class FakeCoordinator(
        private val prepareFailure: Throwable? = null
    ) : ReportExportCoordinator {
        var prepareCalls = 0
        var directSaveCalls = 0
        val deleted = mutableListOf<TemporaryReportExport?>()

        override suspend fun prepare(
            config: ReportExportConfig,
            theme: VisualExportThemeSnapshot
        ): TemporaryReportExport {
            prepareCalls += 1
            prepareFailure?.let { throw it }
            return TemporaryReportExport.Pdf(
                config = config,
                file = File("report.pdf"),
                displayName = "MyLibrary_Monthly_2026_06.pdf"
            )
        }

        override suspend fun saveDirect(
            export: TemporaryReportExport
        ): SavedExportLocation {
            directSaveCalls += 1
            return SavedExportLocation(
                displayName = "MyLibrary_Monthly_2026_06.pdf",
                destination = ExportDestination.DOWNLOADS,
                displayLocation = "Download/MyLibrary"
            )
        }

        override suspend fun savePngToTree(
            export: TemporaryReportExport.PngPages,
            treeUri: Uri
        ) = SavedExportLocation(
            "MyLibrary_Monthly_2026_06_P01.png",
            ExportDestination.USER_SELECTED,
            null
        )

        override suspend fun savePdfToUri(
            export: TemporaryReportExport.Pdf,
            uri: Uri
        ) = SavedExportLocation(
            "MyLibrary_Monthly_2026_06.pdf",
            ExportDestination.USER_SELECTED,
            null
        )

        override fun deleteTemporary(export: TemporaryReportExport?) {
            deleted += export
        }
    }

    private companion object {
        val CONFIG = ReportExportConfig(
            year = 2026,
            month = 6,
            typeId = null,
            outputFormat = ReportOutputFormat.PDF
        )
    }
}
