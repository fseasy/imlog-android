package top.fseasy.imlog.domain.util

import kotlin.coroutines.cancellation.CancellationException

suspend inline fun <T> runSuspendCatching(block: () -> T): Result<T> {
  return try {
    Result.success(block())
  } catch (e: CancellationException) {
    throw e
  } catch (e: Throwable) {
    Result.failure(e)
  }
}
