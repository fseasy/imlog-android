package top.fseasy.imlog.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import top.fseasy.imlog.domain.model.FinishSendingFileWorkerPayload
import top.fseasy.imlog.domain.model.MessageType
import top.fseasy.imlog.domain.repository.BackgroundTaskRunner
import top.fseasy.imlog.domain.util.defaultJson
import java.time.Duration
import javax.inject.Inject

class BackgroundTaskRunnerImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : BackgroundTaskRunner {

    override suspend fun finishSendingAttachmentMessage(payload: FinishSendingFileWorkerPayload) {
        val serializedPayload = defaultJson.encodeToString(payload)
        val workerRequest =
            OneTimeWorkRequestBuilder<FinishSendingFileMessageWorker>().setInputData(
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
