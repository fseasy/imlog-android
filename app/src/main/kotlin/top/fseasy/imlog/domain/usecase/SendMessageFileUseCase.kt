package top.fseasy.imlog.domain.usecase

import timber.log.Timber
import top.fseasy.imlog.domain.model.AbsolutePathModel
import top.fseasy.imlog.domain.model.FileCopyResult
import top.fseasy.imlog.domain.model.MessageFileProcessingErrorType
import top.fseasy.imlog.domain.model.MessageFileProcessingStage
import top.fseasy.imlog.domain.model.MessageFileProcessingStatus
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.domain.model.MessageType
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UriStr
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.repository.MessageRepository
import top.fseasy.imlog.domain.repository.StorageRepository
import javax.inject.Inject

private sealed interface StageCopyToInternalCacheResult {
    data class Success(
        val internalCacheFilename: String,
        val bytesCopied: Long,
        val resultAbsolutePath: AbsolutePathModel,
    ) : StageCopyToInternalCacheResult

    data class Failure(val errorType: MessageFileProcessingErrorType, val retriable: Boolean) :
        StageCopyToInternalCacheResult
}

private typealias ErrorType = MessageFileProcessingErrorType

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
        val messageId = try {
            insertPendingMessage(
                srcUriStr = srcUriStr,
                senderId = userId,
                topicId = topicId,
                messageTimestampMs = messageTimestampMs,
                messageType = messageType
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed in insert pending message stage, can't do anything")
            // can't do anything, just return...
            return
        }

        // 2. copy src file to internal cache
        val originalFilename =
            storageRepository.getDisplayNameOrDefault(srcUriStr, "unknown_audio.mp3")

        when (val copyToInternalResult = copySrcToInternalCacheAndUpdateState(
            userId = userId,
            srcUriStr = srcUriStr,
            messageTimestampMs = messageTimestampMs,
            originalDisplayName = originalFilename
        )) {
            TODO
        }

        val rawFilename = storagePathUseCase.buildUserFriendlyTimestampedFilename(
            messageTimestampMs, originalFilename = displayName
        )
        storagePathUseCase.buildMessageRawFileAbsolutePath(
            userId = userId,
            topicId = topicId,
            timestampMs = messageTimestampMs,
            filename = rawFilename,
        )
    }

    private suspend fun copySrcToInternalCacheAndUpdateState(
        userId: UserId, srcUriStr: UriStr,
        messageTimestampMs: Long,
        originalDisplayName: String,
        messageId: MessageId,
    ): StageCopyToInternalCacheResult {

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
            is FileCopyResult.Error -> return StageCopyToInternalCacheResult.Failure(
                errorType = ErrorType.Copy2InternalFailure,
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
            return StageCopyToInternalCacheResult.Failure(
                errorType = ErrorType.SetInternalCacheToDbException, retriable = true
            )
        }
        if (!isSetSuccess) {
            return StageCopyToInternalCacheResult.Failure(
                errorType = ErrorType.UpdateProcessingStateDbNoEffect,
                retriable = false // illegal state, should be impossible in general
            )
        }
        return StageCopyToInternalCacheResult.Success(
            internalCacheFilename = cacheFilename,
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
        stage: MessageFileProcessingStage,
        errorType: MessageFileProcessingErrorType,
        errorUserRetriable: Boolean,
    ) {
        try {
            messageRepository.fileSendingOnSettingProcessingStatus(
                messageId = messageId,
                status = MessageFileProcessingStatus.Failed,
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
    ) = messageRepository.fileSendingOnInsertingPendingMessage(
        topicId = topicId,
        senderId = senderId,
        messageType = messageType,
        srcUriStr = srcUriStr,
        messageTimestampMs = messageTimestampMs,
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
