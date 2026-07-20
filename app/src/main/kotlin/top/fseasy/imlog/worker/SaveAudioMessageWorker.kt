package top.fseasy.imlog.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.KSerializer
import timber.log.Timber
import top.fseasy.imlog.domain.model.AudioMetadata
import top.fseasy.imlog.domain.model.FinishFileSendingWorkerPayload
import top.fseasy.imlog.domain.model.ImageMetadata
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.usecase.SendMessageFileUseCase
import top.fseasy.imlog.domain.util.defaultJson

/**
 * Why define a generic T: for future payload expansion on different file type
 */
abstract class BaseFinishFileSendingWorker<T>(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext = context, workerParams) {

    /**
     * Actual logic
     */
    protected abstract suspend fun executeUseCase(payload: T)

    /**
     * Used to deserialize the payload
     */
    protected abstract val payloadSerializer: KSerializer<T>

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

class SaveAudioMessageWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val sendFileMessageUseCase: SendMessageFileUseCase,
) : BaseFinishFileSendingWorker<FinishFileSendingWorkerPayload>(context, workerParams) {

    override suspend fun executeUseCase(payload: FinishFileSendingWorkerPayload) {
        sendFileMessageUseCase.finishSendingAudioWorkerLogic(payload)
    }

    override val payloadSerializer: KSerializer<FinishFileSendingWorkerPayload>
        get() = FinishFileSendingWorkerPayload.serializer()
}


class SaveImageMessageWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val sendFileMessageUseCase: SendMessageFileUseCase,
) : BaseFinishFileSendingWorker<FinishFileSendingWorkerPayload>(context, workerParams) {

    override suspend fun executeUseCase(payload: FinishFileSendingWorkerPayload) {
        sendFileMessageUseCase.finishSendingImageWorkerLogic(payload)
    }

    override val payloadSerializer: KSerializer<FinishFileSendingWorkerPayload>
        get() = FinishFileSendingWorkerPayload.serializer()
}