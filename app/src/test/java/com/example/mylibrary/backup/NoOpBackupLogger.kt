package com.example.mylibrary.backup

/**
 * No-op [BackupLogger] for JVM unit tests.
 * Discards all log output without touching [android.util.Log].
 */
object NoOpBackupLogger : BackupLogger {
    override fun warning(message: String, error: Throwable?) {}
    override fun info(message: String) {}
    override fun error(message: String, error: Throwable?) {}
}
