package top.fseasy.imlog.domain.usecase.sendfilemessage.stage

import kotlinx.coroutines.CancellationException
import timber.log.Timber
import top.fseasy.imlog.domain.model.AbsolutePathModel
import top.fseasy.imlog.domain.model.FileCopyResult
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.domain.model.StoragePathModel
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UriStr
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.repository.MessageRepository
import top.fseasy.imlog.domain.repository.StorageRepository
import top.fseasy.imlog.domain.usecase.StoragePathUseCase
import javax.inject.Inject

class CopyFileUseCase @Inject constructor(
    private val storagePathUseCase: StoragePathUseCase,
    private val storageRepository: StorageRepository,
    private val messageRepository: MessageRepository,
) {
    suspend fun copySrcToInternalCacheAndUpdateState(
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
        } catch (e: CancellationException) {
            throw e
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

    suspend fun copyInternalCacheToSharedStorageAndUpdateState(
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
        } catch (e: CancellationException) {
            throw e
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
}


/**
 * Used in copying stages (copy raw to internal cache, to shared storage)
 */
enum class CopyStageFailureType {
    CopyFile, SaveFilenameToDb, UpdateDbIllegalState;
}

sealed interface CopyStageResult {
    data class Success(
        val resultFilename: String,
        val bytesCopied: Long,
        val resultAbsolutePath: AbsolutePathModel,
    ) : CopyStageResult

    data class Failure(val type: CopyStageFailureType, val retryable: Boolean) : CopyStageResult
}


/**
 * This should be case conditioned. So don't put it to the model definition.
 *
 * we think src issue can't be retried.
 */
fun isSrcToInternalCopyErrorRetriable(error: FileCopyResult.Error): Boolean = when (error) {
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


fun isInternalToSharedStorageCopyErrorRetriable(error: FileCopyResult.Error): Boolean =
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
