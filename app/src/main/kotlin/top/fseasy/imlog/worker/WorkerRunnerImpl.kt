package top.fseasy.imlog.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import top.fseasy.imlog.domain.model.AudioMetadata
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.repository.WorkerRunner
import top.fseasy.imlog.domain.usecase.SendMessageFileUseCase
import top.fseasy.imlog.domain.util.defaultJson
import java.time.Duration
import javax.inject.Inject

class WorkerRunnerImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : WorkerRunner {
    override suspend fun finishSendingAudio(
        messageId: MessageId,
        userId: UserId,
        topicId: TopicId,
        messageTimestampMs: Long,
        srcMetadata: AudioMetadata,
        cacheFilename: String,
    ) {
        val workerRequest = OneTimeWorkRequestBuilder<SaveAudioMessageWorker>().setInputData(
            workDataOf(
                KEY_MESSAGE_ID to messageId.value,
                KEY_USER_ID to userId.value,
                KEY_TOPIC_ID to topicId.value,
                KEY_MESSAGE_TIMESTAMP_MS to messageTimestampMs,
                KEY_SRC_FILE_METADATA to defaultJson.encodeToString(srcMetadata),
                KEY_CACHE_FILENAME to cacheFilename
            )
        )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL, Duration.ofMinutes(1)
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST) // Expedited work
            .build()
        WorkManager.getInstance(context)
            .enqueue(workerRequest)
    }
}
