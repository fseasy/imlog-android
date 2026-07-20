package top.fseasy.imlog.domain.repository

import top.fseasy.imlog.domain.model.FinishFileSendingWorkerPayload

interface WorkerRunner {
    suspend fun finishSendingAudio(
        payload: FinishFileSendingWorkerPayload
    )
    suspend fun finishSendingImage(
        payload: FinishFileSendingWorkerPayload
    )
}