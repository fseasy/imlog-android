package top.fseasy.imlog.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException

/**
 * Catch 2 type exceptions:
 * 1. flow construction stage (build db query and flow) by `try-catch`
 * 2. flow execution stage by `Flow.catch`
 */
inline fun <T> safeObserveFlowOrNull(
    crossinline logMessage: () -> String,
    crossinline builder: () -> Flow<T?>,
): Flow<T?> =
    try {
      builder().catch { e ->
        Timber.i(e, "${logMessage()}, emit null")
        emit(null)
      }
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      Timber.i(e, "${logMessage()} (construction)")
      flowOf(null)
    }
