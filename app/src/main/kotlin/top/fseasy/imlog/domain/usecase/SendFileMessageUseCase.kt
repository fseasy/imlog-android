package top.fseasy.imlog.domain.usecase

import timber.log.Timber
import top.fseasy.imlog.data.constants.TIMELINE_THUMBNAIL_COMPRESS_FORMAT
import top.fseasy.imlog.data.constants.TIMELINE_THUMBNAIL_COMPRESS_QUALITY
import top.fseasy.imlog.data.constants.TIMELINE_THUMBNAIL_MAX_SIZE
import top.fseasy.imlog.domain.model.AbsolutePathModel
import top.fseasy.imlog.domain.model.FileCopyResult
import top.fseasy.imlog.domain.model.FileMetadataUnion
import top.fseasy.imlog.domain.model.FinishFileSendingWorkerPayload
import top.fseasy.imlog.domain.model.MessageAudioProcessingErrorStage
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.domain.model.MessageImageProcessingErrorStage
import top.fseasy.imlog.domain.model.MessageProcessingErrorStage
import top.fseasy.imlog.domain.model.MessageType
import top.fseasy.imlog.domain.model.RetryModel
import top.fseasy.imlog.domain.model.StoragePathModel
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UriStr
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.model.toMetadataUnion
import top.fseasy.imlog.domain.repository.DbRunner
import top.fseasy.imlog.domain.repository.MessageFileSource
import top.fseasy.imlog.domain.repository.MessageRepository
import top.fseasy.imlog.domain.repository.StorageRepository
import top.fseasy.imlog.domain.repository.WorkerRunner
import top.fseasy.imlog.domain.service.ImageThumbnailGenerateRequest
import top.fseasy.imlog.domain.service.ThumbnailScale
import top.fseasy.imlog.domain.service.ThumbnailService
import javax.inject.Inject


class SendMessageFileUseCase @Inject constructor(
    private val storagePathUseCase: StoragePathUseCase,
    private val storageRepository: StorageRepository,
    private val messageRepository: MessageRepository,
    private val workerRunner: WorkerRunner,
    private val dbRunner: DbRunner,
    private val thumbnailService: ThumbnailService,
) {
    suspend fun sendVoice(
        audioCacheFilename: String,
        userId: UserId,
        topicId: TopicId,
        messageTimestampMs: Long,
    ) {
        val audioCacheFile =
            storagePathUseCase.buildMessageCacheFileStoragePath(userId, audioCacheFilename)
        val fileMetadata = storageRepository.getAudioMetadataOrNull(audioCacheFile) ?: run {
            Timber.w("Failed to get audio metadata, <$audioCacheFilename> => <$audioCacheFile> is invalid file")
            return
        }
        val messageId = try {
            initializeCacheFileSourceFileMessage(
                cacheFilename = audioCacheFilename,
                senderId = userId,
                topicId = topicId,
                messageTimestampMs = messageTimestampMs,
                messageType = MessageType.VOICE,
                fileMetadata = fileMetadata.toMetadataUnion()
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed in insert pending message stage, can't do anything")
            // can't do anything, just return...
            return
        }
        workerRunner.finishSendingAudio(
            FinishFileSendingWorkerPayload(
                messageId = messageId,
                userId = userId,
                topicId = topicId,
                messageTimestampMs = messageTimestampMs,
                fileMetadata = fileMetadata.toMetadataUnion(),
                cacheFilename = audioCacheFilename
            )
        )
    }

    // If success, will run as normal. else, will set a failure in the db.
    // UI get the failure by the db in async way. So there is no need to return anything.
    suspend fun sendAudio(
        srcUriStr: UriStr,
        userId: UserId,
        topicId: TopicId,
        messageTimestampMs: Long,
    ) {
        // 1. initialize the file message record to db
        val srcMetadata =
            storageRepository.getAudioMetadataOrNull(AbsolutePathModel.UriStrModel(srcUriStr))
                ?: run {
                    Timber.w("Failed to get audio metadata, $srcUriStr is invalid")
                    return
                }
        val messageId = try {
            initializeUriSourceFileMessage(
                srcUriStr = srcUriStr,
                senderId = userId,
                topicId = topicId,
                messageTimestampMs = messageTimestampMs,
                messageType = MessageType.AUDIO,
                fileMetadata = srcMetadata.toMetadataUnion()
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed in insert pending message stage, can't do anything")
            // can't do anything, just return...
            return
        }

        // 2. copy src file to internal cache, then update status
        val copyInternalSuccessResult = when (val result = copySrcToInternalCacheAndUpdateState(
            messageId = messageId,
            userId = userId,
            srcUriStr = srcUriStr,
            messageTimestampMs = messageTimestampMs,
            originalDisplayName = srcMetadata.displayName,
        )) {
            is CopyStageResult.Failure -> return setProcessingTaskFailed(
                messageId = messageId,
                stage = result.type.toAudioProcessingErrorStageOnCopyingInternal(),
                errorUserRetryable = result.retryable
            )

            is CopyStageResult.Success -> result
        }

        workerRunner.finishSendingAudio(
            FinishFileSendingWorkerPayload(
                messageId = messageId,
                userId = userId,
                topicId = topicId,
                messageTimestampMs = messageTimestampMs,
                fileMetadata = srcMetadata.toMetadataUnion(),
                cacheFilename = copyInternalSuccessResult.resultFilename
            )
        )
    }

    /**
     * Shared between sendAudio & sendVoice
     * Should be called in Worker to make the following logic running more stably
     */
    suspend fun finishSendingAudioWorkerLogic(
        payload: FinishFileSendingWorkerPayload,
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
                stage = result.type.toAudioProcessingErrorStageOnCopyingShared(),
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
                stage = result.type.toAudioProcessingErrorStage(),
                errorUserRetryable = false // system will retry it, user don't need to retry.
            )

            is FinishTaskStageResult.Success -> Timber.d("AudioFile from $messageId success")
        }
    }

    suspend fun sendImage(
        srcUriStr: UriStr,
        userId: UserId,
        topicId: TopicId,
        messageTimestampMs: Long,
    ) {
        // 1. initialize the file message record to db
        val fileMetadata =
            storageRepository.getImageMetadataOrNull(AbsolutePathModel.UriStrModel(srcUriStr))
                ?: run {
                    Timber.w("Failed to get image metadata, $srcUriStr is invalid")
                    return
                }
        val messageId = try {
            initializeUriSourceFileMessage(
                srcUriStr = srcUriStr,
                senderId = userId,
                topicId = topicId,
                messageTimestampMs = messageTimestampMs,
                messageType = MessageType.IMAGE,
                fileMetadata = fileMetadata.toMetadataUnion()
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed in insert pending message stage, can't do anything")
            // can't do anything, just return...
            return
        }

        // 2. copy src file to internal cache, then update status
        val copyInternalSuccessResult = when (val result = copySrcToInternalCacheAndUpdateState(
            messageId = messageId,
            userId = userId,
            srcUriStr = srcUriStr,
            messageTimestampMs = messageTimestampMs,
            originalDisplayName = fileMetadata.displayName,
        )) {
            is CopyStageResult.Failure -> return setProcessingTaskFailed(
                messageId = messageId,
                stage = result.type.toImageProcessingErrorStageOnCopyingInternal(),
                errorUserRetryable = result.retryable
            )

            is CopyStageResult.Success -> result
        }

        workerRunner.finishSendingImage(
            FinishFileSendingWorkerPayload(
                messageId = messageId,
                userId = userId,
                topicId = topicId,
                messageTimestampMs = messageTimestampMs,
                fileMetadata = fileMetadata.toMetadataUnion(),
                cacheFilename = copyInternalSuccessResult.resultFilename
            )
        )
    }

    /**
     * Should be called in Worker to make the following logic running more stably
     */
    suspend fun finishSendingImageWorkerLogic(
        payload: FinishFileSendingWorkerPayload,
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
                stage = result.type.toAudioProcessingErrorStageOnCopyingShared(),
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
                stage = result.type.toImageProcessingErrorStage(),
                errorUserRetryable = false // system will retry it, user don't need to retry.
            )

            is FinishTaskStageResult.Success -> Timber.d("MessageFile from $messageId success")
        }
    }

    /**
     * Should be called in Worker to make the following logic running more stably
     */
    private suspend fun runFinishSendingFile(
        payload: FinishFileSendingWorkerPayload,
        errorStageMapper: ProcessingErrorStageMapper,

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
                stage = errorStageMapper.mapRawPersistentRawCopyFailure(result.type),
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
                stage = errorStageMapper.mapFinishTaskFailure(result.type),
                errorUserRetryable = false // system will retry it, user don't need to retry.
            )

            is FinishTaskStageResult.Success -> Timber.d("MessageFile from $messageId success")
        }
    }


}



private fun CopyStageFailureType.toAudioProcessingErrorStageOnCopyingInternal() = when (this) {
    CopyStageFailureType.CopyFile -> MessageAudioProcessingErrorStage.CopySrcToInternalCache
    CopyStageFailureType.SaveFilenameToDb -> MessageAudioProcessingErrorStage.SetInternalFilenameToDb
    CopyStageFailureType.UpdateDbIllegalState -> MessageAudioProcessingErrorStage.IllegalState
}

private fun CopyStageFailureType.toAudioProcessingErrorStageOnCopyingShared() = when (this) {
    CopyStageFailureType.CopyFile -> MessageAudioProcessingErrorStage.CopyToSharedStorage
    CopyStageFailureType.SaveFilenameToDb -> MessageAudioProcessingErrorStage.SetRawFilenameToDb
    CopyStageFailureType.UpdateDbIllegalState -> MessageAudioProcessingErrorStage.IllegalState
}

private fun FinishTaskFailureType.toAudioProcessingErrorStage() = when (this) {
    FinishTaskFailureType.DeleteCacheFile -> MessageAudioProcessingErrorStage.DeleteInternalFileCache
    FinishTaskFailureType.DeleteTaskStateFromDb -> MessageAudioProcessingErrorStage.DeleteTaskStateFromDb
}

private fun CopyStageFailureType.toImageProcessingErrorStageOnCopyingInternal() = when (this) {
    CopyStageFailureType.CopyFile -> MessageImageProcessingErrorStage.CopySrcToInternalCache
    CopyStageFailureType.SaveFilenameToDb -> MessageImageProcessingErrorStage.SetInternalFilenameToDb
    CopyStageFailureType.UpdateDbIllegalState -> MessageImageProcessingErrorStage.IllegalState
}

private fun FinishTaskFailureType.toImageProcessingErrorStage() = when (this) {
    FinishTaskFailureType.DeleteCacheFile -> MessageImageProcessingErrorStage.DeleteInternalFileCache
    FinishTaskFailureType.DeleteTaskStateFromDb -> MessageImageProcessingErrorStage.DeleteTaskStateFromDb
}