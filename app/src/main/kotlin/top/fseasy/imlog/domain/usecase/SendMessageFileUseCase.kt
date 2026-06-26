package top.fseasy.imlog.domain.usecase

import timber.log.Timber
import top.fseasy.imlog.domain.model.AbsolutePathModel
import top.fseasy.imlog.domain.model.FileCopyResult
import top.fseasy.imlog.domain.model.MediaMetadataUnion
import top.fseasy.imlog.domain.model.MessageFileProcessingErrorType as ErrorType
import top.fseasy.imlog.domain.model.MessageFileProcessingStage as ProcessingStage
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.domain.model.MessageType
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UriStr
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.model.toMetadataUnion
import top.fseasy.imlog.domain.repository.MessageRepository
import top.fseasy.imlog.domain.repository.StorageRepository
import javax.inject.Inject

/**
 * Shared on copying stages (copy to internal, copy to shared storage)
 */
private sealed interface CopyStageResult {
    data class Success(
        val resultFilename: String,
        val bytesCopied: Long,
        val resultAbsolutePath: AbsolutePathModel,
    ) : CopyStageResult

    data class Failure(val errorType: ErrorType, val retriable: Boolean) : CopyStageResult
}

class SendMessageFileUseCase @Inject constructor(
    private val storagePathUseCase: StoragePathUseCase,
    private val storageRepository: StorageRepository,
    private val messageRepository: MessageRepository,
) {
    // If success, will run as normal. else, will set a failure in the db.
    // UI get the failure by the db in async way. So there is no need to return anything.
    suspend fun sendAudio(
        srcUriStr: UriStr,
        userId: UserId,
        topicId: TopicId,
        messageTimestampMs: Long,
        messageType: MessageType = MessageType.AUDIO,
    ) {
        // 1. insert pending message
        val srcMetadata = storageRepository.getAudioMetadataOrNull(srcUriStr) ?: run {
            Timber.e("Failed to get audio metadata, $srcUriStr isn't an invalid uri")
            return
        }
        val messageId = try {
            insertPendingMessage(
                srcUriStr = srcUriStr,
                senderId = userId,
                topicId = topicId,
                messageTimestampMs = messageTimestampMs,
                messageType = messageType,
                srcMetadata = srcMetadata.toMetadataUnion()
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed in insert pending message stage, can't do anything")
            // can't do anything, just return...
            return
        }

        // 2. copy src file to internal cache, then update status
        val copyInternalSuccessResult =
            when (val result = copySrcToInternalCacheAndUpdateState(
                messageId = messageId,
                userId = userId,
                srcUriStr = srcUriStr,
                messageTimestampMs = messageTimestampMs,
                originalDisplayName = srcMetadata.displayName,
            )) {
                is CopyStageResult.Failure -> return setProcessingFailed(
                    messageId = messageId,
                    stage = ProcessingStage.CopySrcToInternalCache,
                    errorType = result.errorType,
                    errorUserRetriable = result.retriable
                )

                is CopyStageResult.Success -> result
            }
        // 3. copy internal cache to shared storage
        val copySharedStorageSuccessResult =
            when (val result = copyInternalCacheToSharedStorageAndUpdateState(
                messageId = messageId,
                userId = userId,
                topicId = topicId,
                messageTimestampMs = messageTimestampMs,
                originalDisplayName = srcMetadata.displayName,
                internalCacheFileAbsolutePath = copyInternalSuccessResult.resultAbsolutePath,
                mimeType = srcMetadata.mimeType
            )) {
                is CopyStageResult.Failure -> return setProcessingFailed(
                    messageId = messageId,
                    stage = ProcessingStage.CopyToSharedStorage,
                    errorType = result.errorType,
                    errorUserRetriable = result.retriable
                )

                is CopyStageResult.Success -> result
            }
        // 4. clean cache and processing status record

    }

    private suspend fun copyInternalCacheToSharedStorageAndUpdateState(
        messageId: MessageId,
        userId: UserId,
        topicId: TopicId,
        messageTimestampMs: Long,
        originalDisplayName: String,
        internalCacheFileAbsolutePath: AbsolutePathModel,
        mimeType: String,
    ): CopyStageResult {
        val rawFilename = storagePathUseCase.buildUserFriendlyTimestampedFilename(
            messageTimestampMs, originalFilename = originalDisplayName
        )
        val targetStoragePath = storagePathUseCase.buildMessageRawFileAbsolutePath(
            userId = userId,
            topicId = topicId,
            timestampMs = messageTimestampMs,
            filename = rawFilename,
        )

        val result = storageRepository.copyFile(
            srcAbsolutePath = internalCacheFileAbsolutePath,
            targetPath = targetStoragePath,
            srcMimeType = mimeType
        )
        val copyResult = when (result) {
            is FileCopyResult.Error -> return CopyStageResult.Failure(
                errorType = ErrorType.CopyToSharedStorageFailure,
                retriable = isInternalToSharedStorageCopyErrorRetriable(result)
            )

            is FileCopyResult.Success -> result
        }
        val isSetSuccess = try {
            messageRepository.fileSendingOnSettingRawFilename(
                filename = rawFilename, messageId = messageId
            )
        } catch (e: Exception) {
            Timber.i(e, "Set raw filename failed")
            return CopyStageResult.Failure(
                errorType = ErrorType.SetRawFilenameToDbException, retriable = true
            )
        }
        if (!isSetSuccess) {
            return CopyStageResult.Failure(
                errorType = ErrorType.UpdateProcessingStateDbNoEffect,
                retriable = false // illegal state, should be impossible in general
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
        val cachePath = storagePathUseCase.buildMessageCacheFileAbsolutePath(
            userId, timestampMs = messageTimestampMs, filename = cacheFilename
        )
        val result = storageRepository.copyFile(
            AbsolutePathModel.UriStrModel(srcUriStr), targetPath = cachePath
        )

        val copyResult = when (result) {
            is FileCopyResult.Error -> return CopyStageResult.Failure(
                errorType = ErrorType.CopyToInternalFailure,
                retriable = isSrcToInternalCopyErrorRetriable(result)
            )

            is FileCopyResult.Success -> result
        }
        val isSetSuccess = try {
            messageRepository.fileSendingOnSettingInternalCacheFilename(
                filename = cacheFilename, messageId = messageId
            )
        } catch (e: Exception) {
            Timber.i(e, "Set internal cache filename failed")
            return CopyStageResult.Failure(
                errorType = ErrorType.SetInternalCacheToDbException, retriable = true
            )
        }
        if (!isSetSuccess) {
            return CopyStageResult.Failure(
                errorType = ErrorType.UpdateProcessingStateDbNoEffect,
                retriable = false // illegal state, should be impossible in general
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
    private suspend fun setProcessingFailed(
        messageId: MessageId,
        stage: ProcessingStage,
        errorType: ErrorType,
        errorUserRetriable: Boolean,
    ) {
        try {
            messageRepository.fileSendingOnSettingProcessingStatus(
                messageId = messageId,
                status = ProcessingStatus.Failed,
                stage = stage,
                errorType = errorType,
                errorUserRetriable = errorUserRetriable,
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
    private suspend fun insertPendingMessage(
        srcUriStr: UriStr,
        senderId: UserId,
        topicId: TopicId,
        messageTimestampMs: Long,
        messageType: MessageType,
        srcMetadata: MediaMetadataUnion,
    ) = messageRepository.fileSendingOnInsertingPendingMessage(
        topicId = topicId,
        senderId = senderId,
        messageType = messageType,
        srcUriStr = srcUriStr,
        messageTimestampMs = messageTimestampMs,
        srcMetadata = srcMetadata
    )
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
