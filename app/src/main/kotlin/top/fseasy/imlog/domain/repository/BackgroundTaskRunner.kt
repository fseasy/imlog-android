package top.fseasy.imlog.domain.repository

import top.fseasy.imlog.domain.model.FinishFileSendingWorkerPayload

interface BackgroundTaskRunner {
    suspend fun finishSendingFileMessage(
        payload: FinishFileSendingWorkerPayload,
    )
}