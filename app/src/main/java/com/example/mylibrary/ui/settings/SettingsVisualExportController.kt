package com.example.mylibrary.ui.settings

import android.net.Uri
import android.util.Log
import com.example.mylibrary.export.visual.TemporaryVisualExport
import com.example.mylibrary.export.visual.VisualExportCoordinator
import com.example.mylibrary.export.visual.VisualExportException
import com.example.mylibrary.export.visual.VisualExportRequest
import com.example.mylibrary.export.visual.VisualExportThemeSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsVisualExportState(
    val operation: SettingsVisualExportOperation? = null,
    val safRequest: VisualExportSafRequest? = null,
    val message: String? = null
)

class SettingsVisualExportController(
    private val coordinator: VisualExportCoordinator,
    private val scope: CoroutineScope,
    private val directPictureSaveSupported: Boolean
) {
    private val mutableState = MutableStateFlow(SettingsVisualExportState())
    val state = mutableState.asStateFlow()

    private var task: Job? = null
    private var pendingSafExport: TemporaryVisualExport? = null
    private var nextSafRequestId = 1L

    val isBusy: Boolean
        get() = mutableState.value.operation != null ||
            task?.isActive == true ||
            pendingSafExport != null

    fun start(
        request: VisualExportRequest,
        theme: VisualExportThemeSnapshot
    ): Boolean {
        if (isBusy) return false
        task = scope.launch {
            var temporary: TemporaryVisualExport? = null
            mutableState.value = SettingsVisualExportState(
                operation = SettingsVisualExportOperation.GENERATING
            )
            try {
                temporary = coordinator.prepare(request, theme)
                if (directPictureSaveSupported) {
                    mutableState.value = SettingsVisualExportState(
                        operation = SettingsVisualExportOperation.SAVING
                    )
                    val saved = coordinator.saveToPictures(temporary)
                    mutableState.value = SettingsVisualExportState(
                        message = "已保存到图片：${saved.displayLocation}\n" +
                            saved.displayName
                    )
                } else {
                    pendingSafExport = temporary
                    temporary = null
                    mutableState.value = SettingsVisualExportState(
                        operation =
                            SettingsVisualExportOperation.WAITING_FOR_DESTINATION,
                        safRequest = VisualExportSafRequest(
                            id = nextSafRequestId++,
                            displayName = requireNotNull(pendingSafExport).displayName
                        )
                    )
                }
            } catch (cancelled: CancellationException) {
                mutableState.value = SettingsVisualExportState()
                throw cancelled
            } catch (known: VisualExportException) {
                mutableState.value = SettingsVisualExportState(
                    message = known.message ?: "生成失败，请重试"
                )
            } catch (error: Exception) {
                Log.e(TAG, "Direct visual export failed", error)
                mutableState.value = SettingsVisualExportState(
                    message = "生成失败，请重试"
                )
            } finally {
                deleteTemporaryQuietly(temporary)
            }
        }
        return true
    }

    fun consumeSafRequest() {
        mutableState.value = mutableState.value.copy(safRequest = null)
    }

    fun onSafDestinationResult(destination: Uri?): Boolean {
        val export = pendingSafExport ?: return false
        pendingSafExport = null
        if (destination == null) {
            deleteTemporaryQuietly(export)
            mutableState.value = SettingsVisualExportState()
            return true
        }
        if (task?.isActive == true) {
            pendingSafExport = export
            return false
        }
        task = scope.launch {
            mutableState.value = SettingsVisualExportState(
                operation = SettingsVisualExportOperation.SAVING
            )
            try {
                val saved = coordinator.saveToUri(export, destination)
                mutableState.value = SettingsVisualExportState(
                    message = "已保存到你选择的位置\n${saved.displayName}"
                )
            } catch (cancelled: CancellationException) {
                mutableState.value = SettingsVisualExportState()
                throw cancelled
            } catch (known: VisualExportException) {
                mutableState.value = SettingsVisualExportState(
                    message = known.message ?: "保存失败"
                )
            } catch (error: Exception) {
                Log.e(TAG, "SAF visual export save failed", error)
                mutableState.value = SettingsVisualExportState(
                    message = "保存失败"
                )
            } finally {
                deleteTemporaryQuietly(export)
            }
        }
        return true
    }

    fun consumeMessage() {
        mutableState.value = mutableState.value.copy(message = null)
    }

    fun close() {
        task?.cancel()
        deleteTemporaryQuietly(pendingSafExport)
        pendingSafExport = null
        mutableState.value = SettingsVisualExportState()
    }

    private fun deleteTemporaryQuietly(export: TemporaryVisualExport?) {
        if (export == null) return
        try {
            coordinator.deleteTemporary(export)
        } catch (error: Exception) {
            Log.w(TAG, "Unable to remove visual export temporary file", error)
        }
    }

    private companion object {
        const val TAG = "SettingsVisualExport"
    }
}
