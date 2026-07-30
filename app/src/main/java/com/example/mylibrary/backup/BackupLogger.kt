package com.example.mylibrary.backup

import android.util.Log

/**
 * Minimal logging abstraction used by the backup/restore pipeline so that
 * JVM unit tests are not coupled to [android.util.Log].
 *
 * Production code receives [AndroidBackupLogger] via constructor injection
 * (typically from `AppContainer`).  JVM tests inject a no-op or recording
 * fake implementation.
 *
 * Implementations must NOT log theme resource content, absolute file paths,
 * or other sensitive data.
 */
interface BackupLogger {
    fun warning(message: String, error: Throwable? = null)
    fun info(message: String)
    fun error(message: String, error: Throwable? = null)
}

/**
 * Production [BackupLogger] backed by [android.util.Log].
 */
internal class AndroidBackupLogger(private val tag: String) : BackupLogger {
    override fun warning(message: String, error: Throwable?) {
        if (error != null) Log.w(tag, message, error)
        else Log.w(tag, message)
    }

    override fun info(message: String) {
        Log.i(tag, message)
    }

    override fun error(message: String, error: Throwable?) {
        if (error != null) Log.e(tag, message, error)
        else Log.e(tag, message)
    }
}
