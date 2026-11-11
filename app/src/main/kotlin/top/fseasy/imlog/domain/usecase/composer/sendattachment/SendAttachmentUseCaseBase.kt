package top.fseasy.imlog.domain.usecase.composer.sendattachment

import javax.inject.Inject
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import top.fseasy.imlog.domain.model.FileMetadataUnion
import top.fseasy.imlog.domain.model.FinishSendingFileWorkerPayload
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.domain.model.MessageType
import top.fseasy.imlog.domain.model.StoragePathModel
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.model.toMetadataUnion
import top.fseasy.imlog.domain.repository.BackgroundTaskRunner
import top.fseasy.imlog.domain.repository.StorageRepository
import top.fseasy.imlog.domain.usecase.composer.sendattachment.stage.CopyFileAndUpdateDbUseCase
import top.fseasy.imlog.domain.usecase.composer.sendattachment.stage.CopyStageResult
import top.fseasy.imlog.domain.usecase.composer.sendattachment.stage.CleanupUseCase
import top.fseasy.imlog.domain.usecase.composer.sendattachment.stage.InitializeAttachmentMessageUseCase

/**
 * Split the full process to 3 steps, to suite the UI requirements that sending multiple files ASAP
 *
 * 1. resolve metadata: fast, decide rendering messageType, resolve file metadata [can be parallel]
 * 2. insert initial message to db: fast. [in sequence to avoid ui jump]
 * 3. copy uri to internal, start background task. slower. [can be parallel]
 */
abstract class SendAttachmentUseCaseBase(
  internal val dependencies: SendAttachmentDependencies,
) {
  protected abstract val messageTypeFromSendAction: MessageType

  suspend operator fun invoke(
      srcPath: StoragePathModel,
      userId: UserId,
      topicId: TopicId,
      messageTimestamp: Instant,
  ): Boolean {
    val result =
        resolveMetadata(
            srcPath = srcPath,
            userId = userId,
            topicId = topicId,
            messageTimestamp = messageTimestamp,
        ) ?: return false
    val messageId = insertInitialMessage(result) ?: return false

    return copyToInternalAndStartBackgroundTask(result, messageId)
  }

  /**
   * get metadata, decide the rendering-message-type
   *
   * Run in IO threads for specific time-consuming parts. It's safe to run it in UI thread.
   *
   * @return null when failed
   */
  private suspend fun resolveMetadata(
      srcPath: StoragePathModel,
      userId: UserId,
      topicId: TopicId,
      messageTimestamp: Instant,
  ): ResolveMetadataResult? {
    val messageType = resolveMessageTypeForRender(srcPath)
    val fileMetadata =
        getMetadataOrNullBasedOnMessageType(
            dependencies.storageRepository,
            path = srcPath,
            messageType = messageType,
        )
            ?: run {
              Timber.w(
                  "Failed to get file metadata, $srcPath is invalid, " +
                      "action-msg-type=$messageTypeFromSendAction, render-msg-type=$messageType"
              )
              return null
            }
    return ResolveMetadataResult(
        srcPath = srcPath,
        userId = userId,
        topicId = topicId,
        messageTimestamp = messageTimestamp,
        messageType = messageType,
        fileMetadata = fileMetadata,
    )
  }

  /**
   * insert to db
   *
   * Run in IO threads for specific time-consuming parts. It's safe to run it in UI thread.
   *
   * @return null when failed.
   */
  private suspend fun insertInitialMessage(
    resolveMetadataResult: ResolveMetadataResult,
  ): MessageId? {
    val messageId =
        try {
          dependencies.initializeAttachmentMessageUseCase(
              srcPath = resolveMetadataResult.srcPath,
              senderId = resolveMetadataResult.userId,
              topicId = resolveMetadataResult.topicId,
              messageTimestamp = resolveMetadataResult.messageTimestamp,
              messageType = resolveMetadataResult.messageType,
              fileMetadata = resolveMetadataResult.fileMetadata,
          )
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Timber.e(e, "Failed to initialize file message, can't do anything")
          // can't do anything, just return...
          return null
        }
    return messageId
  }

  /** Runs in IO threads for each necessary statements. */
  private suspend fun copyToInternalAndStartBackgroundTask(
    resolveMetadataResult: ResolveMetadataResult,
    messageId: MessageId,
  ): Boolean {
    val copyResult =
        dependencies.copyFileAndUpdateDbUseCase.onSrcToInternalCache(
            messageId = messageId,
            userId = resolveMetadataResult.userId,
            srcPath = resolveMetadataResult.srcPath,
            messageTimestamp = resolveMetadataResult.messageTimestamp,
            originalDisplayName = resolveMetadataResult.fileMetadata.displayName,
            srcMimeType = resolveMetadataResult.fileMetadata.mimeType,
        )

    return when (copyResult) {
      is CopyStageResult.Failure -> {
        dependencies.cleanupUseCase.onFailure(
            messageId = messageId,
            stage = failureTypeMapper.mapCacheCopyFailure(copyResult.type),
            errorUserRetryable = copyResult.retryable,
        )
        false
      }
      is CopyStageResult.Success -> {
        dependencies.backgroundTaskRunner.finishSendingAttachmentMessage(
            FinishSendingFileWorkerPayload(
                messageId = messageId,
                userId = resolveMetadataResult.userId,
                topicId = resolveMetadataResult.topicId,
                messageTimestamp = resolveMetadataResult.messageTimestamp,
                fileMetadata = resolveMetadataResult.fileMetadata,
                srcPath = resolveMetadataResult.srcPath,
                internalCacheFilename = copyResult.resultFilename,
                messageType = resolveMetadataResult.messageType,
            )
        )
        true
      }
    }
  }

  /**
   * Why: 1. We want to set the messageType to the real actual file for GenericFile.
   * 2. this is the easiest way to unifies the subclasses.
   */
  protected suspend fun resolveMessageTypeForRender(srcPath: StoragePathModel): MessageType =
      when (messageTypeFromSendAction) {
        MessageType.GenericFile -> {
          dependencies.storageRepository.getMimetypeOrNull(srcPath)?.let {
            fileMimeTypeToMessageType(it)
          } ?: MessageType.GenericFile
        }

        else -> messageTypeFromSendAction
      }

  internal abstract val failureTypeMapper: ProcessingFailureTypeMapper

  /** Export this api for background executor, like WorkerManager */
  suspend fun runBackground(payload: FinishSendingFileWorkerPayload) {
    dependencies.backgroundProcessingUseCase(payload, failureTypeMapper)
  }
}

data class SendAttachmentDependencies
@Inject
constructor(
  val backgroundProcessingUseCase: BackgroundProcessingUseCase,
  val storageRepository: StorageRepository,
  val backgroundTaskRunner: BackgroundTaskRunner,
  val initializeAttachmentMessageUseCase: InitializeAttachmentMessageUseCase,
  val copyFileAndUpdateDbUseCase: CopyFileAndUpdateDbUseCase,
  val cleanupUseCase: CleanupUseCase,
)

data class ResolveMetadataResult(
    val srcPath: StoragePathModel,
    val userId: UserId,
    val topicId: TopicId,
    val messageTimestamp: Instant,
    val messageType: MessageType,
    val fileMetadata: FileMetadataUnion,
)

/**  */
private suspend fun getMetadataOrNullBasedOnMessageType(
    repository: StorageRepository,
    path: StoragePathModel,
    messageType: MessageType,
): FileMetadataUnion? =
    when (messageType) {
      MessageType.Image -> repository.getImageMetadataOrNull(path)?.toMetadataUnion()

      MessageType.Video -> repository.getVideoMetadataOrNull(path)?.toMetadataUnion()

      MessageType.Audio,
      MessageType.Voice,
      -> repository.getAudioMetadataOrNull(path)?.toMetadataUnion()

      MessageType.GenericFile -> repository.getGenericFileMetadataOrNull(path)?.toMetadataUnion()

      MessageType.Text -> error("Text MessageType shouldn't use this UseCase")
    }
