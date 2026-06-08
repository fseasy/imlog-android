package top.fseasy.imlog.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import top.fseasy.imlog.data.file.FileManager
import top.fseasy.imlog.data.file.MediaSaveResult
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.domain.model.MessageMediaCopySource
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.repository.MessageRepository
import top.fseasy.imlog.util.toFile
import java.time.Duration

@HiltWorker
class MediaFileProcessWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val fileManager: FileManager,
    private val messageRepository: MessageRepository,
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val KEY_MESSAGE_ID = "message_id"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_TOPIC_ID = "topic_id"
        private const val KEY_MESSAGE_TIMESTAMP_MS = "timestamp_ms"
        private const val KEY_SRC_URI = "src_uri"
        private const val KEY_MAX_RETRIES = "max_retries"
        private const val DEFAULT_MAX_RETRIES = 3


        fun createRequest(
            messageId: MessageId,
            topicId: TopicId,
            senderId: UserId,
            messageTimestampMs: Long,
            copySource: MessageMediaCopySource,
            maxRetries: Int = DEFAULT_MAX_RETRIES,
        ): OneTimeWorkRequest {
            val uri = when (copySource) {
                is MessageMediaCopySource.FromUri -> copySource.uri
                is MessageMediaCopySource.FromFile -> copySource.file.toUri()
            }
            return OneTimeWorkRequestBuilder<MediaFileProcessWorker>().setInputData(
                    workDataOf(
                        KEY_MESSAGE_ID to messageId.value,
                        KEY_USER_ID to senderId.value,
                        KEY_TOPIC_ID to topicId.value,
                        KEY_MESSAGE_TIMESTAMP_MS to messageTimestampMs,
                        KEY_SRC_URI to uri.toString(),
                        KEY_MAX_RETRIES to maxRetries
                    )
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL, Duration.ofMinutes(1)
                )
                .build()
        }
    }

    override suspend fun doWork(): Result {
        val messageId = inputData.getString(KEY_MESSAGE_ID)
            ?.let(::MessageId) ?: return Result.failure()
        val userId = inputData.getString(KEY_USER_ID)
            ?.let(::UserId) ?: return Result.failure()
        val topicId = inputData.getString(KEY_TOPIC_ID)
            ?.let(::TopicId) ?: return Result.failure()
        val messageTimestampMs = inputData.getLong(KEY_MESSAGE_TIMESTAMP_MS, -1)
            .takeIf { it > 0 } ?: return Result.failure()
        val srcUri = inputData.getString(KEY_SRC_URI)
            ?.toUri() ?: return Result.failure()
        val maxRetries = inputData.getInt(KEY_MAX_RETRIES, DEFAULT_MAX_RETRIES)
        try {
            val saveResult = fileManager.saveMessageMedia(
                userId = userId,
                topicId = topicId,
                srcUri = srcUri,
                messageTimestampMs = messageTimestampMs,
            )
            val savedMedia = when (saveResult) {
                is MediaSaveResult.MediaSavePermissionError,
                is MediaSaveResult.MediaSaveUnexpectedError,
                is MediaSaveResult.MediaSaveSrcInvalidError,
                    -> return Result.failure()

                is MediaSaveResult.SavedMedia -> saveResult
            }

            messageRepository.finishMediaProcessing(messageId, savedMedia)

            return Result.success()
        } catch (e: Exception) {
            // WorkManager 内置的重试计数
            return if (runAttemptCount >= maxRetries) {
                Result.failure(workDataOf("cause" to "$e"))
            } else {
                Result.retry()
            }
        }
    }
}