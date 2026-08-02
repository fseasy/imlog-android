package top.fseasy.imlog.worker

import androidx.work.workDataOf
import timber.log.Timber
import androidx.work.ListenableWorker.Result as WorkerResult

internal fun failureWithLog(reason: String, throwable: Throwable? = null): WorkerResult {
  Timber.w(t = throwable, "WorkManager failure on <$reason>")
  return WorkerResult.failure(workDataOf("error" to reason))
}
