package top.fseasy.imlog.domain.repository

import top.fseasy.imlog.domain.model.FinishSendingFileWorkerPayload

interface BackgroundTaskRunner {
    suspend fun finishSendingFileMessage(
        payload: FinishSendingFileWorkerPayload,
    )
}