package top.fseasy.imlog.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import top.fseasy.imlog.domain.model.FinishFileSendingWorkerPayload
import top.fseasy.imlog.domain.model.MessageType
import top.fseasy.imlog.domain.repository.BackgroundTaskRunner
import top.fseasy.imlog.domain.util.defaultJson
import java.time.Duration
import javax.inject.Inject

class BackgroundTaskRunnerImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : BackgroundTaskRunner {

    override suspend fun finishSendingFileMessage(payload: FinishFileSendingWorkerPayload) {
        val serializedPayload = defaultJson.encodeToString(payload)
        when (payload.messageType) {
            MessageType.AUDIO,
            MessageType.VOICE,
                -> enqueueFinishFileSendingWorker<SaveAudioMessageWorker>(
                serializedPayload
            )

            MessageType.IMAGE -> enqueueFinishFileSendingWorker<SaveImageMessageWorker>(
                serializedPayload
            )

            else -> error("Unsupported message type in sending background task")
        }
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
