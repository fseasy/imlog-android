package top.fseasy.imlog.domain.usecase.sendfilemessage

import timber.log.Timber
import top.fseasy.imlog.domain.model.FinishSendingFileWorkerPayload
import top.fseasy.imlog.domain.usecase.StoragePathUseCase
import top.fseasy.imlog.domain.usecase.sendfilemessage.stage.CopyFileUseCase
import top.fseasy.imlog.domain.usecase.sendfilemessage.stage.CopyStageResult
import top.fseasy.imlog.domain.usecase.sendfilemessage.stage.FinishProcessingUseCase
import top.fseasy.imlog.domain.usecase.sendfilemessage.stage.FinishProcessingStageResult
import top.fseasy.imlog.domain.usecase.sendfilemessage.stage.GenerateThumbnailStageResult
import top.fseasy.imlog.domain.usecase.sendfilemessage.stage.GenerateThumbnailUseCase
import javax.inject.Inject

class BackgroundProcessingUseCase @Inject constructor(
    private val storagePathUseCase: StoragePathUseCase,
    private val copyFileUseCase: CopyFileUseCase,
    private val finishProcessingUseCase: FinishProcessingUseCase,
    private val generateThumbnailUseCase: GenerateThumbnailUseCase,
) {
    /**
     * Should be called in background process (like Worker in Android)
     * to make the following logic running more stably
     */
    internal suspend operator fun invoke(
        payload: FinishSendingFileWorkerPayload,
        failureTypeMapper: ProcessingFailureTypeMapper,
    ) {
        val internalCacheFilePath = storagePathUseCase.buildMessageCacheFileStoragePath(
            userId = payload.userId, filename = payload.cacheFilename
        )
        val messageId = payload.messageId
        // 1. generate thumbnail
        when (val result = generateThumbnailUseCase(
            messageId = messageId,
            userId = payload.userId,
            topicId = payload.topicId,
            messageTimestampMs = payload.messageTimestampMs,
            messageType = payload.messageType,
            srcUriStr = payload.srcUriStr,
            cacheFilePath = internalCacheFilePath,
            fileMetadata = payload.fileMetadata,
        )) {
            is GenerateThumbnailStageResult.Failure -> return finishProcessingUseCase.onFailure(
                messageId = messageId,
                stage = failureTypeMapper.mapThumbnailFailure(result.type),
                errorUserRetryable = result.retryable
            )

            is GenerateThumbnailStageResult.Success,
            is GenerateThumbnailStageResult.Skip,
                -> Unit
        }
        // 2. copy internal cache to shared storage
        when (val result = copyFileUseCase.copyInternalCacheToSharedStorageAndUpdateState(
            messageId = messageId,
            userId = payload.userId,
            topicId = payload.topicId,
            messageTimestampMs = payload.messageTimestampMs,
            originalDisplayName = payload.fileMetadata.displayName,
            internalCacheFilePath = internalCacheFilePath,
            mimeType = payload.fileMetadata.mimeType
        )) {
            is CopyStageResult.Failure -> return finishProcessingUseCase.onFailure(
                messageId = messageId,
                stage = failureTypeMapper.mapSharedStorageCopyFailure(result.type),
                errorUserRetryable = result.retryable
            )

            is CopyStageResult.Success -> Unit
        }

        // 3. finish processing on success: clean cache and processing status record
        when (val result = finishProcessingUseCase.onSuccess(
            messageId, internalCachePathModel = internalCacheFilePath
        )) {
            is FinishProcessingStageResult.Failure -> return finishProcessingUseCase.onFailure(
                messageId = messageId,
                stage = failureTypeMapper.mapFinishTaskFailure(result.type),
                errorUserRetryable = false // system will retry it, user don't need to retry.
            )

            is FinishProcessingStageResult.Success -> Timber.d("MessageFile from $messageId success")
        }
    }
}