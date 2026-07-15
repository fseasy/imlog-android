package top.fseasy.imlog.domain.usecase

import timber.log.Timber
import top.fseasy.imlog.domain.model.AbsolutePathModel
import top.fseasy.imlog.domain.model.AudioMetadata
import top.fseasy.imlog.domain.model.FileCopyResult
import top.fseasy.imlog.domain.model.FileDeleteResult
import top.fseasy.imlog.domain.model.MediaMetadataUnion
import top.fseasy.imlog.domain.model.MessageAudioProcessingErrorStage
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.domain.model.MessageProcessingErrorStage
import top.fseasy.imlog.domain.model.MessageType
import top.fseasy.imlog.domain.model.StoragePathModel
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UriStr
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.model.toMetadataUnion
import top.fseasy.imlog.domain.repository.MessageRepository
import top.fseasy.imlog.domain.repository.StorageRepository
import top.fseasy.imlog.domain.repository.WorkerRunner
import javax.inject.Inject

/**
 * Shared on copying stages (copy to internal, copy to shared storage)
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

private enum class FinishTaskFailureType {
    DeleteCacheFile, DeleteTaskStateFromDb
}

private sealed interface FinishTaskStageResult {
    data object Success : FinishTaskStageResult
    data class Failure(val type: FinishTaskFailureType) : FinishTaskStageResult
}

class SendMessageFileUseCase @Inject constructor(
    private val storagePathUseCase: StoragePathUseCase,
    private val storageRepository: StorageRepository,
    private val messageRepository: MessageRepository,
    private val workerRunner: WorkerRunner,
) {
    // If success, will run as normal. else, will set a failure in the db.
    // UI get the failure by the db in async way. So there is no need to return anything.
    suspend fun sendAudioInstantLogic(
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
            initializeFileMessage(
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

        workerRunner.finishSendingAudio()
    }

    private suspend fun sendAudioWorkerLogic(
        messageId: MessageId,
        userId: UserId,
        topicId: TopicId,
        messageTimestampMs: Long,
        srcMetadata: AudioMetadata,
        cacheFilename: String,
    ) {
        val internalCacheFilePath = storagePathUseCase.buildMessageCacheFileStoragePath(
            userId = userId,
            timestampMs = messageTimestampMs,
            filename = cacheFilename
        )
        // 1. copy internal cache to shared storage
        when (val result = copyInternalCacheToSharedStorageAndUpdateState(
            messageId = messageId,
            userId = userId,
            topicId = topicId,
            messageTimestampMs = messageTimestampMs,
            originalDisplayName = srcMetadata.displayName,
            internalCacheFilePath = internalCacheFilePath,
            mimeType = srcMetadata.mimeType
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
            messageId,
            internalCachePathModel = internalCacheFilePath
        )) {
            is FinishTaskStageResult.Failure -> return setProcessingTaskFailed(
                messageId = messageId,
                stage = result.type.toAudioProcessingErrorStage(),
                errorUserRetryable = false // system will retry it, user don't need to retry.
            )

            is FinishTaskStageResult.Success -> Timber.d("AudioFile from $messageId success")
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
            srcPath = internalCacheFilePath,
            targetPath = targetStoragePath,
            srcMimeType = mimeType
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
            userId, timestampMs = messageTimestampMs, filename = cacheFilename
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
    private suspend fun initializeFileMessage(
        srcUriStr: UriStr,
        senderId: UserId,
        topicId: TopicId,
        messageTimestampMs: Long,
        messageType: MessageType,
        srcMetadata: MediaMetadataUnion,
    ) = messageRepository.initializeFileMessage(
        topicId = topicId,
        senderId = senderId,
        messageType = messageType,
        srcUriStr = srcUriStr,
        messageTimestampMs = messageTimestampMs,
        srcMetadata = srcMetadata
    )

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