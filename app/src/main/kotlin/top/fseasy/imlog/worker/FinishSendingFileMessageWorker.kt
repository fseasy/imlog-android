package top.fseasy.imlog.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import top.fseasy.imlog.domain.model.FinishSendingFileWorkerPayload
import top.fseasy.imlog.domain.usecase.sendattachment.SendRunBackgroundUseCaseFactory
import top.fseasy.imlog.domain.util.defaultJson

class FinishSendingFileMessageWorker
@AssistedInject
constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val sendRunBackgroundUseCase: SendRunBackgroundUseCaseFactory,
) : CoroutineWorker(appContext = context, workerParams) {

  suspend fun executeUseCase(payload: FinishSendingFileWorkerPayload) {
    sendRunBackgroundUseCase.get(payload.messageType).runBackground(payload)
  }

  override suspend fun doWork(): Result {
    val serializedPayload =
        inputData.getString(KEY_INPUT_PAYLOAD)
            ?: return failureWithLog("InputData: no payload found")
    val payload: FinishSendingFileWorkerPayload =
        try {
          defaultJson.decodeFromString(serializedPayload)
        } catch (e: Exception) {
          return failureWithLog(
              "Failed to deserialize payload: $serializedPayload",
              throwable = e,
          )
        }
    val maxRetries = DEFAULT_MAX_RETRIES
    return try {
      executeUseCase(payload)
      Result.success()
    } catch (e: Exception) {
      // runAttemptCount is WorkManager's internal counter
      return if (runAttemptCount >= maxRetries) {
        failureWithLog(e.message ?: "$e")
      } else {
        Timber.w(e, "retry $runAttemptCount/$maxRetries")
        Result.retry()
      }
    }
  }
}
