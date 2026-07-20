package top.fseasy.imlog.domain.usecase

import androidx.compose.animation.scaleIn
import timber.log.Timber
import top.fseasy.imlog.data.constants.TIMELINE_THUMBNAIL_COMPRESS_FORMAT
import top.fseasy.imlog.data.constants.TIMELINE_THUMBNAIL_COMPRESS_QUALITY
import top.fseasy.imlog.data.constants.TIMELINE_THUMBNAIL_MAX_SIZE
import top.fseasy.imlog.domain.model.AbsolutePathModel
import top.fseasy.imlog.domain.model.FileCopyResult
import top.fseasy.imlog.domain.model.FileDeleteResult
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
import top.fseasy.imlog.domain.service.ThumbnailFormat
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

    private suspend fun copyInternalCacheToSharedStorageAndUpdateState(
        messageId: MessageId,
        userId: UserId,
        topicId: TopicId,
        messageTimestampMs: Long,
        originalDisplayName: String,
        internalCacheFilePath: StoragePathModel,
        mimeType: String,
    ): CopyStageResult {
        val rawFilename = storagePathUseCase.buildUserFriendlyTimestampedFilename(
            messageTimestampMs, originalFilename = originalDisplayName
        )
        val targetStoragePath = storagePathUseCase.buildMessageRawFileStoragePath(
            userId = userId,
            topicId = topicId,
            timestampMs = messageTimestampMs,
            filename = rawFilename,
        )

        val result = storageRepository.copyFile(
            srcPath = internalCacheFilePath, targetPath = targetStoragePath, srcMimeType = mimeType
        )
        val copyResult = when (result) {
            is FileCopyResult.Error -> return CopyStageResult.Failure(
                type = CopyStageFailureType.CopyFile,
                retryable = isInternalToSharedStorageCopyErrorRetriable(result)
            )

            is FileCopyResult.Success -> result
        }
        val isSetSuccess = try {
            messageRepository.setFileMessageRawFilename(
                filename = rawFilename, messageId = messageId
            )
        } catch (e: Exception) {
            Timber.i(e, "Set raw filename failed")
            return CopyStageResult.Failure(
                type = CopyStageFailureType.SaveFilenameToDb, retryable = true
            )
        }
        if (!isSetSuccess) {
            return CopyStageResult.Failure(
                type = CopyStageFailureType.UpdateDbIllegalState,
                retryable = false // illegal state, should be impossible in general
            )
        }
        return CopyStageResult.Success(
            resultFilename = rawFilename,
            bytesCopied = copyResult.bytesCopied,
            resultAbsolutePath = copyResult.resultAbsolutePath
        )
    }

    private suspend fun copySrcToInternalCacheAndUpdateState(
        messageId: MessageId,
        userId: UserId,
        srcUriStr: UriStr,
        messageTimestampMs: Long,
        originalDisplayName: String,
    ): CopyStageResult {

        val cacheFilename = storagePathUseCase.buildTimestampedFilename(
            timestampMs = messageTimestampMs, originalFilename = originalDisplayName
        )
        val cachePath = storagePathUseCase.buildMessageCacheFileStoragePath(
            userId, filename = cacheFilename
        )
        val result = storageRepository.copyFile(
            AbsolutePathModel.UriStrModel(srcUriStr), targetPath = cachePath
        )

        val copyResult = when (result) {
            is FileCopyResult.Error -> return CopyStageResult.Failure(
                type = CopyStageFailureType.CopyFile,
                retryable = isSrcToInternalCopyErrorRetriable(result)
            )

            is FileCopyResult.Success -> result
        }
        val isSetSuccess = try {
            messageRepository.setFileProcessingInternalCacheFilename(
                filename = cacheFilename, messageId = messageId
            )
        } catch (e: Exception) {
            Timber.i(e, "Set internal cache filename failed")
            return CopyStageResult.Failure(
                type = CopyStageFailureType.SaveFilenameToDb, retryable = true
            )
        }
        if (!isSetSuccess) {
            return CopyStageResult.Failure(
                type = CopyStageFailureType.UpdateDbIllegalState,
                retryable = false // illegal state, should be impossible in general
            )
        }
        return CopyStageResult.Success(
            resultFilename = cacheFilename,
            bytesCopied = copyResult.bytesCopied,
            resultAbsolutePath = copyResult.resultAbsolutePath
        )
    }

    /**
     * No exception will be thrown.
     * Because no place to handle the error as it's the one to record the error
     */
    private suspend fun setProcessingTaskFailed(
        messageId: MessageId,
        stage: MessageProcessingErrorStage,
        errorUserRetryable: Boolean,
    ) {
        try {
            messageRepository.setFileProcessingTaskFail(
                messageId = messageId,
                stage = stage,
                errorUserRetryable = errorUserRetryable,
            )
        } catch (e: Exception) {
            Timber.e(
                e,
                "Failed to Set Message File Processing failure. Have to leave it to be uncertain status"
            )
            // can't do anything. Leave it to be
        }
    }

    /**
     * @throws Throwable
     */
    private suspend fun initializeUriSourceFileMessage(
        srcUriStr: UriStr,
        senderId: UserId,
        topicId: TopicId,
        messageTimestampMs: Long,
        messageType: MessageType,
        fileMetadata: FileMetadataUnion,
    ): MessageId = dbRunner.runInTransaction(retry = RetryModel.OnAnyException) {
        val messageId = messageRepository.syncInsertInitialFileMessage(
            topicId = topicId,
            senderId = senderId,
            type = messageType,
            timestampMs = messageTimestampMs,
            fileMetadata = fileMetadata
        )
        messageRepository.syncInsertInitialFileProcessingTaskState(
            messageId = messageId,
            fileSource = MessageFileSource.FromUriStr(srcUriStr),
            taskStartTime = messageTimestampMs
        )
        messageId
    }

    /**
     * @param cacheFilename: obey the @StoragePathUseCase.buildMessageCacheFileStoragePath
     * @throws Throwable
     */
    private suspend fun initializeCacheFileSourceFileMessage(
        cacheFilename: String,
        senderId: UserId,
        topicId: TopicId,
        messageTimestampMs: Long,
        messageType: MessageType,
        fileMetadata: FileMetadataUnion,
    ): MessageId = dbRunner.runInTransaction(retry = RetryModel.OnAnyException) {
        val messageId = messageRepository.syncInsertInitialFileMessage(
            topicId = topicId,
            senderId = senderId,
            type = messageType,
            timestampMs = messageTimestampMs,
            fileMetadata = fileMetadata
        )
        messageRepository.syncInsertInitialFileProcessingTaskState(
            messageId = messageId,
            fileSource = MessageFileSource.FromMessageCacheFile(cacheFilename),
            taskStartTime = messageTimestampMs
        )
        messageId
    }

    /**
     * @param srcUriStr As Ui will first render with source uri, so first try this value
     *                  to share the thumbnail result if possible, which is inherent supported by Coil
     */
    private suspend fun generateThumbnailAndUpdateState(
        messageId: MessageId,
        srcUriStr: UriStr?,
        cacheFilepath: StoragePathModel.InternalOnly,
        messageType: MessageType,
    ) {
        val scale = ThumbnailScale.FitMaxSize(maxSize = TIMELINE_THUMBNAIL_MAX_SIZE)
        val quality = TIMELINE_THUMBNAIL_COMPRESS_QUALITY
        val format = TIMELINE_THUMBNAIL_COMPRESS_FORMAT
        suspend fun generateImageThumbnail(input: AbsolutePathModel): ByteArray {
            val request = ImageThumbnailGenerateRequest(
                input = input,
                scale = scale,
                quality = quality,
                format = format,
            )
            return thumbnailService.generateImageThumbnail(request)
        }

        // @throw Exception when failed on all
        suspend fun generateOnTowSource(executeGenerate: suspend (AbsolutePathModel) -> ByteArray): ByteArray {
            // First try source, continue if none or fail
            if (srcUriStr != null) {
                try {
                    return executeGenerate(AbsolutePathModel.UriStrModel(srcUriStr))
                } catch (e: Exception) {
                    Timber.w(e, "Generate Thumbnail failed on source uri: $srcUriStr")
                    // go to next
                }
            }
            // then try cache as input, throw when fail
            return executeGenerate(
                storageRepository.resolveStoragePathToAbsolutePathsWithoutCreating(
                    cacheFilepath
                )
                    .last()
            )
        }

        when (messageType) {

        }
    }

    /**
     * Run in IO in each io parts.
     * No exception will be thrown.
     */
    private suspend fun finishProcessingTask(
        messageId: MessageId,
        internalCachePathModel: StoragePathModel.InternalOnly,
    ): FinishTaskStageResult {
        when (storageRepository.deleteFile(internalCachePathModel)) {
            is FileDeleteResult.FileNotExist,
            is FileDeleteResult.Success,
                -> Unit

            is FileDeleteResult.Error -> return FinishTaskStageResult.Failure(FinishTaskFailureType.DeleteCacheFile)
        }

        try {
            messageRepository.deleteFileProcessingTaskState(messageId)
        } catch (e: Exception) {
            Timber.i(e, "delete file processing task state failed")
            return FinishTaskStageResult.Failure(FinishTaskFailureType.DeleteTaskStateFromDb)
        }
        return FinishTaskStageResult.Success
    }
}


/**
 * This should be case conditioned. So don't put it to the model definition.
 *
 * we think src issue can't be retried.
 */
private fun isSrcToInternalCopyErrorRetriable(error: FileCopyResult.Error): Boolean = when (error) {
    is FileCopyResult.Error.CopyIOError,
    is FileCopyResult.Error.CopyUnexpected,
    is FileCopyResult.Error.TgtNotFound,
    is FileCopyResult.Error.TgtOpenUnexpected,
    is FileCopyResult.Error.TgtPermissionDenied,
        -> true

    is FileCopyResult.Error.SrcNotFound,
    is FileCopyResult.Error.SrcOpenUnexpected,
    is FileCopyResult.Error.SrcPermissionDenied,
        -> false
}

private fun isInternalToSharedStorageCopyErrorRetriable(error: FileCopyResult.Error): Boolean =
    when (error) {
        is FileCopyResult.Error.CopyIOError,
        is FileCopyResult.Error.CopyUnexpected,
            -> true

        is FileCopyResult.Error.TgtOpenUnexpected,
        is FileCopyResult.Error.TgtNotFound,
        is FileCopyResult.Error.TgtPermissionDenied,
        is FileCopyResult.Error.SrcNotFound,
        is FileCopyResult.Error.SrcOpenUnexpected,
        is FileCopyResult.Error.SrcPermissionDenied,
            -> false
    }


/**
 * Used in copying stages (copy raw to internal cache, to shared storage)
 */
private enum class CopyStageFailureType {
    CopyFile, SaveFilenameToDb, UpdateDbIllegalState;
}

private sealed interface CopyStageResult {
    data class Success(
        val resultFilename: String,
        val bytesCopied: Long,
        val resultAbsolutePath: AbsolutePathModel,
    ) : CopyStageResult

    data class Failure(val type: CopyStageFailureType, val retryable: Boolean) : CopyStageResult
}

private enum class ThumbnailTaskFailureType {
    Generate, SaveFilenameToDb
}

/**
 * Used in finishing task stage
 */
private enum class FinishTaskFailureType {
    DeleteCacheFile, DeleteTaskStateFromDb
}

private sealed interface FinishTaskStageResult {
    data object Success : FinishTaskStageResult
    data class Failure(val type: FinishTaskFailureType) : FinishTaskStageResult
}

private data class ProcessingErrorStageMapper(
    val mapCacheRawCopyFailure: (CopyStageFailureType) -> MessageProcessingErrorStage,
    val mapRawPersistentRawCopyFailure: (CopyStageFailureType) -> MessageProcessingErrorStage,
    val mapThumbnailFailure: (ThumbnailTaskFailureType) -> MessageProcessingErrorStage,
    val mapFinishTaskFailure: (FinishTaskFailureType) -> MessageProcessingErrorStage,
)

private

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