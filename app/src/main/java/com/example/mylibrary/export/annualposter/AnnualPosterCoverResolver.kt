package com.example.mylibrary.export.annualposter

import android.content.Context
import android.util.Log
import com.example.mylibrary.data.image.CoverInputValidator
import com.example.mylibrary.data.image.resolveStoredCoverFile
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

suspend fun resolveAnnualPosterCovers(
    context: Context,
    snapshot: AnnualPosterSnapshot,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO
): AnnualPosterSnapshot = withContext(ioDispatcher) {
    val appContext = context.applicationContext
    val activeContext = coroutineContext
    resolveAnnualPosterCoverPaths(snapshot) { path ->
        activeContext.ensureActive()
        try {
            val file = resolveStoredCoverFile(appContext, path)
            if (file == null) {
                null
            } else {
                val metadata = CoverInputValidator.validate(file)
                AnnualPosterCoverMetadata(
                    path = path,
                    width = metadata.width,
                    height = metadata.height
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "Skipping invalid annual poster cover: $path", error)
            null
        }
    }
}

private const val TAG = "AnnualPosterCovers"
