package top.fseasy.imlog.worker;

import androidx.work.ListenableWorker.Result as WorkerResult
import androidx.work.workDataOf
import timber.log.Timber

internal fun failureWithLog(reason: String): WorkerResult {
    Timber.e("WorkManager failure on <$reason>")
    return WorkerResult.failure(workDataOf("error" to reason))
}