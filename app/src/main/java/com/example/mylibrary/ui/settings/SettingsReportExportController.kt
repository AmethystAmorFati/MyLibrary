package com.example.mylibrary.ui.settings

import android.net.Uri
import android.util.Log
import com.example.mylibrary.export.report.ExportDestination
import com.example.mylibrary.export.report.ReportExportCoordinator
import com.example.mylibrary.export.report.ReportExportException
import com.example.mylibrary.export.report.SavedExportLocation
import com.example.mylibrary.export.report.TemporaryReportExport
import com.example.mylibrary.export.visual.VisualExportThemeSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsReportExportState(
    val operation: SettingsReportExportOperation? = null,
    val safRequest: ReportExportSafRequest? = null,
    val message: String? = null
)

enum class ReportSafDestination {
    DIRECTORY,
    PDF_DOCUMENT
}

data class ReportExportSafRequest(
    val id: Long,
    val destination: ReportSafDestination,
    val displayName: String
)

class SettingsReportExportController(
    private val coordinator: ReportExportCoordinator,
    private val scope: CoroutineScope,
    private val directSaveSupported: Boolean
) {
    private val mutableState = MutableStateFlow(SettingsReportExportState())
    val state = mutableState.asStateFlow()

    private var task: Job? = null
    private var pending: TemporaryReportExport? = null
    private var nextRequestId = 1L

    val isBusy: Boolean
        get() = mutableState.value.operation != null ||
                task?.isActive == true ||
                pending != null

    fun start(
        config: ReportExportConfig,
        theme: VisualExportThemeSnapshot
    ): Boolean {
        if (isBusy) return false

        task = scope.launch {
            var temporary: TemporaryReportExport? = null

            mutableState.value = SettingsReportExportState(
                operation = SettingsReportExportOperation.RESOLVING_AND_RENDERING
            )

            try {
                temporary = coordinator.prepare(config, theme)

                if (directSaveSupported) {
                    mutableState.value = SettingsReportExportState(
                        operation = SettingsReportExportOperation.SAVING
                    )

                    val saved = coordinator.saveDirect(temporary)

                    mutableState.value = SettingsReportExportState(
                        message = successMessage(saved)
                    )
                } else {
                    pending = temporary
                    temporary = null

                    val export = requireNotNull(pending)

                    mutableState.value = SettingsReportExportState(
                        operation = SettingsReportExportOperation.WAITING_FOR_DESTINATION,
                        safRequest = ReportExportSafRequest(
                            id = nextRequestId++,
                            destination = when (export) {
                                is TemporaryReportExport.PngPages ->
                                    ReportSafDestination.DIRECTORY

                                is TemporaryReportExport.Pdf ->
                                    ReportSafDestination.PDF_DOCUMENT
                            },
                            displayName = when (export) {
                                is TemporaryReportExport.PngPages ->
                                    "MyLibrary Reports"

                                is TemporaryReportExport.Pdf ->
                                    export.displayName
                            }
                        )
                    )
                }
            } catch (cancelled: CancellationException) {
                mutableState.value = SettingsReportExportState()
                throw cancelled
            } catch (known: ReportExportException) {
                mutableState.value = SettingsReportExportState(
                    message = known.message ?: "生成失败，请重试"
                )
            } catch (error: Exception) {
                Log.e(TAG, "Report export failed", error)

                mutableState.value = SettingsReportExportState(
                    message = "生成失败，请重试"
                )
            } finally {
                deleteTemporaryQuietly(temporary)
            }
        }

        return true
    }

    fun consumeSafRequest() {
        mutableState.value = mutableState.value.copy(
            safRequest = null
        )
    }

    fun onSafResult(destination: Uri?): Boolean {
        val export = pending ?: return false
        pending = null

        if (destination == null) {
            deleteTemporaryQuietly(export)
            mutableState.value = SettingsReportExportState()
            return true
        }

        if (task?.isActive == true) {
            pending = export
            return false
        }

        task = scope.launch {
            mutableState.value = SettingsReportExportState(
                operation = SettingsReportExportOperation.SAVING
            )

            try {
                val saved = when (export) {
                    is TemporaryReportExport.PngPages ->
                        coordinator.savePngToTree(
                            export = export,
                            treeUri = destination
                        )

                    is TemporaryReportExport.Pdf ->
                        coordinator.savePdfToUri(
                            export = export,
                            uri = destination
                        )
                }

                mutableState.value = SettingsReportExportState(
                    message = successMessage(saved)
                )
            } catch (cancelled: CancellationException) {
                mutableState.value = SettingsReportExportState()
                throw cancelled
            } catch (known: ReportExportException) {
                mutableState.value = SettingsReportExportState(
                    message = known.message ?: "保存失败"
                )
            } catch (error: Exception) {
                Log.e(TAG, "SAF report save failed", error)

                mutableState.value = SettingsReportExportState(
                    message = "保存失败"
                )
            } finally {
                deleteTemporaryQuietly(export)
            }
        }

        return true
    }

    fun consumeMessage() {
        mutableState.value = mutableState.value.copy(
            message = null
        )
    }

    fun close() {
        task?.cancel()
        task = null

        deleteTemporaryQuietly(pending)
        pending = null

        mutableState.value = SettingsReportExportState()
    }

    private fun successMessage(
        saved: SavedExportLocation
    ): String {
        return when (saved.destination) {
            ExportDestination.PICTURES -> {
                val location = saved.displayLocation
                    ?.takeIf(String::isNotBlank)
                    ?: "Pictures/MyLibrary"

                buildString {
                    append("已保存到图片：")
                    append(location)
                    append('\n')
                    append(saved.fileSummary())
                }
            }

            ExportDestination.DOWNLOADS -> {
                val location = saved.displayLocation
                    ?.takeIf(String::isNotBlank)
                    ?: "Downloads/MyLibrary"

                buildString {
                    append("PDF 已保存到下载：")
                    append(location)
                    append('\n')
                    append(saved.displayName)
                }
            }

            ExportDestination.USER_SELECTED -> {
                val prefix = if (
                    saved.displayName.endsWith(
                        suffix = ".pdf",
                        ignoreCase = true
                    )
                ) {
                    "PDF "
                } else {
                    ""
                }

                buildString {
                    append(prefix)
                    append("已保存到你选择的位置")
                    append('\n')
                    append(saved.fileSummary())
                }
            }
        }
    }

    private fun SavedExportLocation.fileSummary(): String {
        return if (fileCount > 1) {
            "$displayName（共 $fileCount 张）"
        } else {
            displayName
        }
    }

    private fun deleteTemporaryQuietly(
        export: TemporaryReportExport?
    ) {
        if (export == null) return

        try {
            coordinator.deleteTemporary(export)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(
                TAG,
                "Unable to remove report temporary output",
                error
            )
        }
    }

    private companion object {
        const val TAG = "SettingsReportExport"
    }
}