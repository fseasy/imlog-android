package top.fseasy.imlog.data.util

import android.database.sqlite.SQLiteConstraintException
import kotlinx.coroutines.delay
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

suspend fun <T> retrySQLiteOnKeyConflict(
    maxAttempts: Int = 3,
    block: suspend () -> T,
): T {
    repeat(maxAttempts - 1) { attempt ->
        try {
            return block()
        } catch (e: SQLiteConstraintException) {
            Timber.w(
                e,
                "SQLite insert get key conflict, attempt %d/%d, retrying...", attempt, maxAttempts
            )
        }
    }
    try {
        return block()
    } catch (e: SQLiteConstraintException) {
        Timber.w(e, "SQLite insert failed all(${maxAttempts}) attempts, raise exception")
        throw e
    }
}

suspend fun <T> retryOnAnyException(
    maxAttempts: Int = 3,
    delayMs: Long = 1500L,
    blockLogName: String = "block",
    block: suspend () -> T,
): T {
    repeat(maxAttempts - 1) { attempt ->
        try {
            return block()
        } catch (e: Exception) {
            Timber.w(
                e,
                "$blockLogName attempt %d/%d failed, retrying...", attempt, maxAttempts
            )
        }
        delay(delayMs.milliseconds)
    }
    try {
        return block()
    } catch (e: Exception) {
        Timber.w(e, "$blockLogName failed all(${maxAttempts}) attempts, raise exception")
        throw e
    }
}