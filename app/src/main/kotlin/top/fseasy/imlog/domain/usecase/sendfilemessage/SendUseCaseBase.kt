package top.fseasy.imlog.domain.usecase.sendfilemessage

import timber.log.Timber
import top.fseasy.imlog.domain.model.FinishFileSendingWorkerPayload
import top.fseasy.imlog.domain.repository.StorageRepository
import top.fseasy.imlog.domain.repository.WorkerRunner
import top.fseasy.imlog.domain.usecase.StoragePathUseCase
import top.fseasy.imlog.domain.usecase.sendfilemessage.stage.CopyFileUseCase
import top.fseasy.imlog.domain.usecase.sendfilemessage.stage.FinishProcessingUseCase
import top.fseasy.imlog.domain.usecase.sendfilemessage.stage.InitializeFileMessageUseCase
import javax.inject.Inject

abstract class SendUseCaseBase @Inject constructor(
    private val storagePathUseCase: StoragePathUseCase,
    private val storageRepository: StorageRepository,
    private val workerRunner: WorkerRunner,
    private val initializeFileMessageUseCase: InitializeFileMessageUseCase,
    private val copyFileUseCase: CopyFileUseCase,
    private val finishProcessingUseCase: FinishProcessingUseCase,
) {

    /**
     * Should be called in Worker to make the following logic running more stably
     */
    internal suspend fun runFinishSendingFile(
        payload: FinishFileSendingWorkerPayload,
        failureTypeMapper: ProcessingFailureTypeMapper,

        ) {
        val internalCacheFilePath = storagePathUseCase.buildMessageCacheFileStoragePath(
            userId = payload.userId, filename = payload.cacheFilename
        )
        val messageId = payload.messageId
        // 1. copy internal cache to shared storage
        when (val result = copyInternalCacheToSharedStorageAndUpdateState(
            messageId = messageId,
            userId = payload.userId,
            topicId = payload.topicId,
            messageTimestampMs = payload.messageTimestampMs,
            originalDisplayName = payload.fileMetadata.displayName,
            internalCacheFilePath = internalCacheFilePath,
            mimeType = payload.fileMetadata.mimeType
        )) {
            is CopyStageResult.Failure -> return setProcessingTaskFailed(
                messageId = messageId,
                stage = failureTypeMapper.mapRawPersistentRawCopyFailure(result.type),
                errorUserRetryable = result.retryable
            )

            is CopyStageResult.Success -> Unit
        }

        // 2. clean cache and processing status record
        when (val result = finishProcessingTask(
            messageId, internalCachePathModel = internalCacheFilePath
        )) {
            is FinishTaskStageResult.Failure -> return setProcessingTaskFailed(
                messageId = messageId,
                stage = failureTypeMapper.mapFinishTaskFailure(result.type),
                errorUserRetryable = false // system will retry it, user don't need to retry.
            )

            is FinishTaskStageResult.Success -> Timber.d("MessageFile from $messageId success")
        }
    }
}