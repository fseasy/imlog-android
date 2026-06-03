package top.fseasy.imlog.util

import android.database.sqlite.SQLiteConstraintException
import timber.log.Timber

suspend fun <T> retrySQLiteOnKeyConflict(
    maxAttempts: Int = 3,
    block: suspend () -> T
): T {
    repeat (maxAttempts - 1) { attempt ->
        try {
            return block()
        } catch (e: SQLiteConstraintException) {
            Timber.w(e,
                "SQLite insert get key conflict, attempt %d/%d, retrying...", attempt, maxAttempts)
        }
    }
    try {
        return block()
    } catch (e: SQLiteConstraintException) {
        Timber.w(e, "SQLite insert failed all(${maxAttempts}) attempts, raise exception")
        throw e
    }
}