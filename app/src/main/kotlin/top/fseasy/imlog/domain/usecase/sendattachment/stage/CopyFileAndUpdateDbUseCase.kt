package top.fseasy.imlog.domain.usecase.sendattachment.stage

import javax.inject.Inject
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import top.fseasy.imlog.domain.model.FileCopyResult
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.domain.model.StoragePathModel
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.repository.MessageRepository
import top.fseasy.imlog.domain.repository.StorageRepository
import top.fseasy.imlog.domain.usecase.StoragePathUseCase
import top.fseasy.imlog.domain.usecase.sendattachment.AttachmentPathUseCase

class CopyFileAndUpdateDbUseCase
@Inject
constructor(
    private val storagePathUseCase: StoragePathUseCase,
    private val storageRepository: StorageRepository,
    private val messageRepository: MessageRepository,
    private val attachmentPathUseCase: AttachmentPathUseCase,
) {

  /**
   * 1. copy src to internal cache if necessary (skip for already in internal cache src)
   * 2. update AttachmentProcessingInternalCacheFilename
   */
  suspend fun onSrcToInternalCache(
      srcPath: StoragePathModel,
      messageId: MessageId,
      userId: UserId,
      messageTimestamp: Instant,
      originalDisplayName: String,
      srcMimeType: String?,
  ): CopyStageResult {

    val copyResult =
        copySrcToInternalIfNecessary(
            srcPath = srcPath,
            userId = userId,
            messageTimestamp = messageTimestamp,
            originalDisplayName = originalDisplayName,
            srcMimeType = srcMimeType,
        )
    val resultFilename =
        (copyResult as? CopyStageResult.Success)?.resultFilename ?: return copyResult

    val isSetSuccess =
        try {
          messageRepository.setAttachmentProcessingInternalCacheFilename(
              filename = resultFilename,
              messageId = messageId,
          )
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Timber.i(e, "Set internal cache filename failed")
          return CopyStageResult.Failure(
              type = CopyStageFailureType.SaveFilenameToDb,
              retryable = true,
          )
        }
    if (!isSetSuccess) {
      return CopyStageResult.Failure(
          type = CopyStageFailureType.UpdateDbIllegalState,
          retryable = false, // illegal state, should be impossible in general
      )
    }
    return copyResult
  }

  suspend fun onInternalToSharedStorage(
      messageId: MessageId,
      userId: UserId,
      topicId: TopicId,
      messageTimestamp: Instant,
      originalDisplayName: String,
      internalCacheFilePath: StoragePathModel,
      mimeType: String,
  ): CopyStageResult {
    val rawFilename =
        storagePathUseCase.buildUserFriendlyTimestampedFilename(
            messageTimestamp,
            originalFilename = originalDisplayName,
        )
    val targetStoragePath =
        storagePathUseCase.buildMessageRawFileStoragePath(
            userId = userId,
            topicId = topicId,
            timestamp = messageTimestamp,
            filename = rawFilename,
        )

    val result =
        storageRepository.copyFile(
            srcPath = internalCacheFilePath,
            targetPath = targetStoragePath,
            srcMimeType = mimeType,
        )
    if (result is FileCopyResult.Error)
        return CopyStageResult.Failure(
            type = CopyStageFailureType.CopyFile,
            retryable = isInternalToSharedStorageCopyErrorRetriable(result),
        )
    val isSetSuccess =
        try {
          messageRepository.setAttachmentMessageRawFilename(
              filename = rawFilename,
              messageId = messageId,
          )
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Timber.i(e, "Set raw filename failed")
          return CopyStageResult.Failure(
              type = CopyStageFailureType.SaveFilenameToDb,
              retryable = true,
          )
        }
    if (!isSetSuccess) {
      return CopyStageResult.Failure(
          type = CopyStageFailureType.UpdateDbIllegalState,
          retryable = false, // illegal state, should be impossible in general
      )
    }
    return CopyStageResult.Success(
        resultFilename = rawFilename,
        skipCopy = false,
    )
  }

  private suspend fun copySrcToInternalIfNecessary(
      srcPath: StoragePathModel,
      userId: UserId,
      messageTimestamp: Instant,
      originalDisplayName: String,
      srcMimeType: String?,
  ): CopyStageResult {
    // Special Condition: if srcPath is already in internal cache location, like the Voice Source
    // File.
    // We'll just skip the copying.
    if (attachmentPathUseCase.isPathAlreadyInInternalCache(srcPath, userId)) {
      return CopyStageResult.Success(
          resultFilename = srcPath.fullRelativePath.last(),
          skipCopy = true,
      )
    }
    // Do normal copy
    val cacheFilename =
        attachmentPathUseCase.generateInternalFilename(messageTimestamp, originalDisplayName)
    val cachePath = attachmentPathUseCase.generateInternalCachePath(userId, cacheFilename)

    val result =
        storageRepository.copyFile(srcPath, targetPath = cachePath, srcMimeType = srcMimeType)

    return when (result) {
      is FileCopyResult.Error ->
          CopyStageResult.Failure(
              type = CopyStageFailureType.CopyFile,
              retryable = isSrcToInternalCopyErrorRetriable(result),
          )

      is FileCopyResult.Success -> CopyStageResult.Success(cacheFilename, skipCopy = false)
    }
  }
}

/** Used in copying stages (copy raw to internal cache, to shared storage) */
enum class CopyStageFailureType {
  CopyFile,
  SaveFilenameToDb,
  UpdateDbIllegalState,
}

sealed interface CopyStageResult {
  data class Success(
      val resultFilename: String,
      val skipCopy: Boolean,
  ) : CopyStageResult

  data class Failure(val type: CopyStageFailureType, val retryable: Boolean) : CopyStageResult
}

/**
 * This should be case conditioned. So don't put it to the model definition.
 *
 * we think src issue can't be retried.
 */
fun isSrcToInternalCopyErrorRetriable(error: FileCopyResult.Error): Boolean =
    when (error) {
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
