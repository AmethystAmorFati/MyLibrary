package com.example.mylibrary.ui.settings

import android.net.Uri
import com.example.mylibrary.export.visual.AnnualPosterCategory
import com.example.mylibrary.export.visual.TemporaryVisualExport
import com.example.mylibrary.export.visual.VisualExportCoordinator
import com.example.mylibrary.export.visual.VisualExportError
import com.example.mylibrary.export.visual.VisualExportException
import com.example.mylibrary.export.visual.VisualExportRequest
import com.example.mylibrary.export.visual.VisualExportThemeSnapshot
import com.example.mylibrary.export.visual.SavedVisualExportLocation
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsVisualExportControllerTest {
    @Test
    fun directSaveRejectsDuplicateAndPublishesSuccessThenIdle() = runTest {
        val coordinator = FakeCoordinator()
        val controller = controller(coordinator, directSave = true)

        assertTrue(controller.start(CALENDAR, theme()))
        assertFalse(controller.start(ANNUAL, theme()))
        advanceUntilIdle()

        assertEquals(listOf(CALENDAR), coordinator.preparedRequests)
        assertEquals(1, coordinator.pictureSaveCalls)
        assertNull(controller.state.value.operation)
        assertEquals(
            "已保存到图片：Pictures/MyLibrary\n" +
                "MyLibrary_Calendar_2026_07.png",
            controller.state.value.message
        )
        assertEquals(1, coordinator.deleted.size)
    }

    @Test
    fun noDataAndRenderFailureReturnToIdleWithSafeMessages() = runTest {
        val noData = FakeCoordinator(
            prepareFailure = VisualExportException(
                VisualExportError.NO_DATA,
                "该月份没有可导出的记录"
            )
        )
        val noDataController = controller(noData, directSave = true)
        noDataController.start(CALENDAR, theme())
        advanceUntilIdle()
        assertNull(noDataController.state.value.operation)
        assertEquals(
            "该月份没有可导出的记录",
            noDataController.state.value.message
        )

        val renderFailure = FakeCoordinator(
            prepareFailure = VisualExportException(
                VisualExportError.RENDER_FAILED,
                "生成失败，请重试"
            )
        )
        val renderController = controller(renderFailure, directSave = true)
        renderController.start(ANNUAL, theme())
        advanceUntilIdle()
        assertNull(renderController.state.value.operation)
        assertEquals("生成失败，请重试", renderController.state.value.message)
    }

    @Test
    fun directSaveFailureReturnsToIdleAndDeletesTemporaryFile() = runTest {
        val coordinator = FakeCoordinator(
            pictureSaveFailure = VisualExportException(
                VisualExportError.SAVE_FAILED,
                "保存失败"
            )
        )
        val controller = controller(coordinator, directSave = true)

        controller.start(CALENDAR, theme())
        advanceUntilIdle()

        assertNull(controller.state.value.operation)
        assertEquals("保存失败", controller.state.value.message)
        assertEquals(1, coordinator.deleted.size)
    }

    @Test
    fun cancellationPropagatesWithoutFailureMessageAndCleansTemporary() = runTest {
        val coordinator = FakeCoordinator(
            pictureSaveFailure = CancellationException("cancelled")
        )
        val controller = controller(coordinator, directSave = true)

        controller.start(CALENDAR, theme())
        advanceUntilIdle()

        assertNull(controller.state.value.operation)
        assertNull(controller.state.value.message)
        assertEquals(1, coordinator.deleted.size)
    }

    @Test
    fun legacyAndroidRequestsSafAndCancelReturnsIdleWithoutMessage() = runTest {
        val coordinator = FakeCoordinator()
        val controller = controller(coordinator, directSave = false)

        controller.start(ANNUAL, theme())
        advanceUntilIdle()

        assertEquals(
            SettingsVisualExportOperation.WAITING_FOR_DESTINATION,
            controller.state.value.operation
        )
        assertEquals(
            "MyLibrary_Annual_All_2026.png",
            controller.state.value.safRequest?.displayName
        )
        controller.consumeSafRequest()
        assertNull(controller.state.value.safRequest)

        assertTrue(controller.onSafDestinationResult(null))
        assertNull(controller.state.value.operation)
        assertNull(controller.state.value.message)
        assertEquals(1, coordinator.deleted.size)
    }

    private fun TestScope.controller(
        coordinator: FakeCoordinator,
        directSave: Boolean
    ) = SettingsVisualExportController(
        coordinator = coordinator,
        scope = this,
        directPictureSaveSupported = directSave
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
        private val prepareFailure: Throwable? = null,
        private val pictureSaveFailure: Throwable? = null
    ) : VisualExportCoordinator {
        val preparedRequests = mutableListOf<VisualExportRequest>()
        val deleted = mutableListOf<TemporaryVisualExport?>()
        var pictureSaveCalls = 0

        override suspend fun prepare(
            request: VisualExportRequest,
            theme: VisualExportThemeSnapshot
        ): TemporaryVisualExport {
            preparedRequests += request
            prepareFailure?.let { throw it }
            return TemporaryVisualExport(
                request = request,
                displayName = when (request) {
                    is VisualExportRequest.Calendar ->
                        "MyLibrary_Calendar_2026_07.png"
                    is VisualExportRequest.AnnualPoster ->
                        "MyLibrary_Annual_All_2026.png"
                },
                temporaryFile = File("temporary.png")
            )
        }

        override suspend fun saveToPictures(
            export: TemporaryVisualExport
        ): SavedVisualExportLocation {
            pictureSaveCalls += 1
            pictureSaveFailure?.let { throw it }
            return SavedVisualExportLocation(
                export.displayName,
                "Pictures/MyLibrary"
            )
        }

        override suspend fun saveToUri(
            export: TemporaryVisualExport,
            destination: Uri
        ) = SavedVisualExportLocation(export.displayName, null)

        override fun deleteTemporary(export: TemporaryVisualExport?) {
            deleted += export
        }
    }

    private companion object {
        val CALENDAR = VisualExportRequest.Calendar(2026, 7)
        val ANNUAL = VisualExportRequest.AnnualPoster(
            2026,
            AnnualPosterCategory.ALL
        )
    }
}
