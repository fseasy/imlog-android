package top.fseasy.imlog.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.KSerializer
import timber.log.Timber
import top.fseasy.imlog.domain.model.FinishSendingFileWorkerPayload
import top.fseasy.imlog.domain.usecase.sendfilemessage.SendAudioMessageUseCase
import top.fseasy.imlog.domain.usecase.sendfilemessage.SendImageMessageUseCase
import top.fseasy.imlog.domain.usecase.sendfilemessage.SendVideoMessageUseCase
import top.fseasy.imlog.domain.usecase.sendfilemessage.SendVoiceMessageUseCase
import top.fseasy.imlog.domain.util.defaultJson

/**
 * Why define a generic T: for future payload expansion on different file type
 */
abstract class FinishSendingFileMessageWorkerBase(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext = context, workerParams) {

    /**
     * Actual logic
     */
    protected abstract suspend fun executeUseCase(payload: FinishSendingFileWorkerPayload)

    /**
     * Used to deserialize the payload
     */
    private val payloadSerializer: KSerializer<FinishSendingFileWorkerPayload>
        get() = FinishSendingFileWorkerPayload.serializer()

    override suspend fun doWork(): Result {
        val serializedPayload = inputData.getString(KEY_INPUT_PAYLOAD)
            ?: return failureWithLog("InputData: no payload found")
        val payload =
            try {
                defaultJson.decodeFromString(payloadSerializer, serializedPayload)
            } catch (e: Exception) {
                return failureWithLog(
                    "Failed to deserialize payload: $serializedPayload",
                    throwable = e
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

class FinishSendingAudioMessageWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val sendAudioMessageUseCase: SendAudioMessageUseCase,
) : FinishSendingFileMessageWorkerBase(context, workerParams) {

    override suspend fun executeUseCase(payload: FinishSendingFileWorkerPayload) {
        sendAudioMessageUseCase.runBackground(payload)
    }
}


class FinishSendingImageMessageWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val sendImageMessageUseCase: SendImageMessageUseCase,
) : FinishSendingFileMessageWorkerBase(context, workerParams) {

    override suspend fun executeUseCase(payload: FinishSendingFileWorkerPayload) {
        sendImageMessageUseCase.runBackground(payload)
    }
}

class FinishSendingVideoMessageWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val sendVideoMessageUseCase: SendVideoMessageUseCase,
) : FinishSendingFileMessageWorkerBase(context, workerParams) {

    override suspend fun executeUseCase(payload: FinishSendingFileWorkerPayload) {
        sendVideoMessageUseCase.runBackground(payload)
    }
}

class FinishSendingVoiceMessageWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val sendVoiceMessageUseCase: SendVoiceMessageUseCase,
) : FinishSendingFileMessageWorkerBase(context, workerParams) {

    override suspend fun executeUseCase(payload: FinishSendingFileWorkerPayload) {
        sendVoiceMessageUseCase.runBackground(payload)
    }
}