package top.fseasy.imlog.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import top.fseasy.imlog.domain.model.AudioMetadata
import top.fseasy.imlog.domain.model.FinishFileSendingWorkerPayload
import top.fseasy.imlog.domain.model.ImageMetadata
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
        payload: FinishFileSendingWorkerPayload,
    ) {
        enqueueFinishFileSendingWorker<SaveAudioMessageWorker>(
            defaultJson.encodeToString(payload)
        )
    }

    override suspend fun finishSendingImage(
        payload: FinishFileSendingWorkerPayload,
    ) {
        enqueueFinishFileSendingWorker<SaveImageMessageWorker>(
            defaultJson.encodeToString(payload)
        )
    }

    private inline fun <reified W : ListenableWorker> enqueueFinishFileSendingWorker(
        serializedPayload: String,
    ) {
        val workerRequest = OneTimeWorkRequestBuilder<W>().setInputData(
            workDataOf(KEY_INPUT_PAYLOAD to serializedPayload)
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
