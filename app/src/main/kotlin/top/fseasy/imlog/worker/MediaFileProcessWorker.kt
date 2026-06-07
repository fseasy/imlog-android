package top.fseasy.imlog.worker

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import top.fseasy.imlog.data.file.FileManager
import top.fseasy.imlog.data.file.MediaSaveResult
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.repository.MessageRepository

@HiltWorker
class MediaFileProcessWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val fileManager: FileManager,
    private val messageRepository: MessageRepository,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val messageId = inputData.getString("KEY_MESSAGE_ID")
            ?.let(::MessageId) ?: return Result.failure()
        val userId = inputData.getString("KEY_USER_ID")
            ?.let(::UserId) ?: return Result.failure()
        val topicId = inputData.getString("KEY_TOPIC_ID")
            ?.let(::TopicId) ?: return Result.failure()
        val uri = inputData.getString("KEY_URI")
            ?.toUri() ?: return Result.failure()
        val messageTimestampMs = inputData.getLong("KEY_MESSAGE_TIMESTAMP_MS", -1)
            .takeIf { it > 0 } ?: return Result.failure()

        val saveResult = fileManager.saveMessageMedia(
            userId = userId,
            topicId = topicId,
            srcUri = uri,
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

        // TODO: exception handling when 1. result.failure, SQL operation failure, Dispatcher
        return Result.success()
    }
}