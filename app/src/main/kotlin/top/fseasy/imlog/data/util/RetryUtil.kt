package top.fseasy.imlog.data.util

import android.database.sqlite.SQLiteConstraintException
import kotlinx.coroutines.delay
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

suspend fun <T> retry(
    maxAttempts: Int,
    delayMs: Long,
    blockLogName: String,
    shouldRetry: (Throwable) -> Boolean,
    block: suspend () -> T,
): T {
    val totalAttempts = maxAttempts.coerceAtLeast(1)

    repeat(totalAttempts - 1) { index ->
        val attempt = index + 1
        try {
            return block()
        } catch (e: Throwable) {
            if (shouldRetry(e)) {
                Timber.w(
                    e,
                    "%s attempt %d/%d failed, retrying in %d ms...",
                    blockLogName,
                    attempt,
                    totalAttempts,
                    delayMs
                )
                if (delayMs > 0) {
                    delay(delayMs.milliseconds)
                }
            } else {
                throw e
            }
        }
    }

    try {
        return block()
    } catch (e: Throwable) {
        if (shouldRetry(e)) {
            Timber.w(
                e, "%s failed all (%d) attempts, raising exception", blockLogName, totalAttempts
            )
        }
        throw e
    }
}

suspend fun <T> retrySQLiteOnKeyConflict(
    maxAttempts: Int = 3,
    block: suspend () -> T,
): T = retry(
    maxAttempts,
    delayMs = 1500L,
    blockLogName = "SQLiteExecute",
    shouldRetry = { it is SQLiteConstraintException },
    block = block
)

suspend fun <T> retryOnAnyException(
    maxAttempts: Int = 3,
    delayMs: Long = 1500L,
    blockLogName: String = "block",
    block: suspend () -> T,
): T = retry(maxAttempts, delayMs, blockLogName, shouldRetry = { it is Exception }, block = block)