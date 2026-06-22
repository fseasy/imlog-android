package top.fseasy.imlog.data.util

import android.database.sqlite.SQLiteConstraintException
import kotlinx.coroutines.CancellationException
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
    val actualDelayMs = delayMs.coerceAtLeast(1).milliseconds

    var attempt = 1
    while (true) {
        try {
            return block()
        } catch (e: Throwable) {
            // 1. Must throw CancellationException to ensure coroutine cancellation
            if (e is CancellationException) {
                throw e
            }
            // 2. check exception to see if it can retry.
            if (!shouldRetry(e)) {
                Timber.w(
                    e,
                    "%s attempt %d/%d reach non-retriable condition. Throw",
                    blockLogName,
                    attempt,
                    totalAttempts,
                )
                throw e
            }
            // 3. if reach max attempts
            if (attempt >= totalAttempts) {
                Timber.w(
                    e,
                    "%s failed all (%d) attempts, raising last exception",
                    blockLogName,
                    totalAttempts
                )
                throw e
            }
            // retry continue, log here and increase counter
            Timber.w(
                e,
                "%s attempt %d/%d failed, retrying in %d ms...",
                blockLogName,
                attempt,
                totalAttempts,
                delayMs
            )
            attempt++
            delay(actualDelayMs)
        }
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