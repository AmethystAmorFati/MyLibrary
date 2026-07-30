package com.example.mylibrary.ui.settings

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mylibrary.export.visual.TemporaryVisualExport
import com.example.mylibrary.export.visual.VisualExportCoordinator
import com.example.mylibrary.export.visual.VisualExportRequest
import com.example.mylibrary.export.visual.VisualExportThemeSnapshot
import com.example.mylibrary.export.visual.SavedVisualExportLocation
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsVisualExportSafTest {
    @Test
    fun safSuccessSavesOncePublishesSuccessAndDeletesTemporary() {
        val coordinator = FakeCoordinator()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = SettingsVisualExportController(
            coordinator = coordinator,
            scope = scope,
            directPictureSaveSupported = false
        )
        try {
            assertTrue(controller.start(REQUEST, THEME))
            assertEquals(
                SettingsVisualExportOperation.WAITING_FOR_DESTINATION,
                controller.state.value.operation
            )

            controller.consumeSafRequest()
            assertTrue(
                controller.onSafDestinationResult(
                    Uri.parse("content://com.example.mylibrary.test/export.png")
                )
            )

            assertEquals(1, coordinator.uriSaveCalls)
            assertEquals(
                "已保存到你选择的位置\nMyLibrary_Calendar_2026_07.png",
                controller.state.value.message
            )
            assertNull(controller.state.value.operation)
            assertEquals(1, coordinator.deletedCalls)
        } finally {
            controller.close()
            scope.cancel()
        }
    }

    private class FakeCoordinator : VisualExportCoordinator {
        var uriSaveCalls = 0
        var deletedCalls = 0

        override suspend fun prepare(
            request: VisualExportRequest,
            theme: VisualExportThemeSnapshot
        ) = TemporaryVisualExport(
            request = request,
            displayName = "MyLibrary_Calendar_2026_07.png",
            temporaryFile = File("temporary.png")
        )

        override suspend fun saveToPictures(
            export: TemporaryVisualExport
        ) = SavedVisualExportLocation(export.displayName, "Pictures/MyLibrary")

        override suspend fun saveToUri(
            export: TemporaryVisualExport,
            destination: Uri
        ): SavedVisualExportLocation {
            uriSaveCalls += 1
            return SavedVisualExportLocation(export.displayName, null)
        }

        override fun deleteTemporary(export: TemporaryVisualExport?) {
            deletedCalls += 1
        }
    }

    private companion object {
        val REQUEST = VisualExportRequest.Calendar(2026, 7)
        val THEME = VisualExportThemeSnapshot(
            backgroundColor = 0,
            textPrimary = 1,
            textSecondary = 2,
            border = 3,
            placeholderColor = 4,
            headingTypeface = null,
            contentTypeface = null
        )
    }
}
