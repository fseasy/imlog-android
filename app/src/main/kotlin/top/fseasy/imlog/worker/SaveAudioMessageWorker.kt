package top.fseasy.imlog.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import top.fseasy.imlog.domain.model.AudioMetadata
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.usecase.SendMessageFileUseCase
import top.fseasy.imlog.domain.util.defaultJson

class SaveAudioMessageWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val sendFileMessageUseCase: SendMessageFileUseCase,
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        val messageId = inputData.getString(KEY_MESSAGE_ID)
            ?.let(::MessageId) ?: return failureWithLog("InputData: No MessageId")
        val userId = inputData.getString(KEY_USER_ID)
            ?.let(::UserId) ?: return failureWithLog("InputData: no UserId")
        val topicId = inputData.getString(KEY_TOPIC_ID)
            ?.let(::TopicId) ?: return failureWithLog("InputData: no TopicId")
        val messageTimestampMs = inputData.getLong(KEY_MESSAGE_TIMESTAMP_MS, -1)
            .takeIf { it > 0 } ?: return failureWithLog("InputData: no MessageTimeMs")
        val srcMetadataJson = inputData.getString(KEY_SRC_FILE_METADATA)
            ?: return failureWithLog("InputData: no src-file-metadata")
        val srcMetadata =
            try {
                defaultJson.decodeFromString<AudioMetadata>(srcMetadataJson)
            } catch (e: Exception) {
                return failureWithLog("Failed to deserialize src-metadata-json: $srcMetadataJson, e=${e.message}")
            }
        val cacheFilename = inputData.getString(KEY_CACHE_FILENAME)
            ?: return failureWithLog("InputData: no cache-filename")
        val maxRetries = DEFAULT_MAX_RETRIES
        return try {
            sendFileMessageUseCase.finishSendingAudioWorkerLogic(
                messageId = messageId,
                userId = userId,
                topicId = topicId,
                messageTimestampMs = messageTimestampMs,
                srcMetadata = srcMetadata,
                cacheFilename = cacheFilename,
            )
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