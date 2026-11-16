package top.fseasy.imlog.common

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

inline fun <T> runSuspendCatching(block: () -> T): Result<T> {
  return try {
    Result.success(block())
  } catch (e: CancellationException) {
    throw e
  } catch (e: Throwable) {
    Result.failure(e)
  }
}

fun CoroutineScope.launchCatching(
    onError: (Throwable) -> Unit = { e -> Timber.e(e, "Coroutine got unhandled error ") },
    block: suspend CoroutineScope.() -> Unit,
): Job = launch {
  try {
    block()
  } catch (c: CancellationException) {
    throw c
  } catch (t: Throwable) {
    onError(t)
  }
}
