package top.fseasy.imlog.domain.usecase.sendattachment

import kotlinx.coroutines.CancellationException
import timber.log.Timber
import top.fseasy.imlog.domain.model.AbsolutePathModel
import top.fseasy.imlog.domain.model.FileMetadataUnion
import top.fseasy.imlog.domain.model.FinishSendingFileWorkerPayload
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.domain.model.MessageType
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UriStr
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.model.toMetadataUnion
import top.fseasy.imlog.domain.repository.BackgroundTaskRunner
import top.fseasy.imlog.domain.repository.StorageRepository
import top.fseasy.imlog.domain.usecase.StoragePathUseCase
import top.fseasy.imlog.domain.usecase.sendattachment.stage.CopyFileUseCase
import top.fseasy.imlog.domain.usecase.sendattachment.stage.CopyStageResult
import top.fseasy.imlog.domain.usecase.sendattachment.stage.FinishProcessingUseCase
import top.fseasy.imlog.domain.usecase.sendattachment.stage.InitializeAttachmentMessageUseCase
import javax.inject.Inject
import kotlin.time.Instant

abstract class SendUseCaseBase(
    private val backgroundProcessingUseCase: BackgroundProcessingUseCase,
    private val storageRepository: StorageRepository,
) {
  protected abstract val messageTypeFromSendAction: MessageType

  /**
   * Why: 1. We want to set the messageType to the real actual file for GenericFile.
   * 2. this is the easist way to unifie the subclasses.
   */
  protected suspend fun resolveMessageTypeForRender(srcPath: AbsolutePathModel): MessageType =
      when (messageTypeFromSendAction) {
        MessageType.GenericFile -> {
          storageRepository.getMimetypeOrNull(srcPath)?.let {
            fileMimeTypeToMessageType(it)
          } ?: MessageType.GenericFile
        }

        else -> messageTypeFromSendAction
      }

  internal abstract val failureTypeMapper: ProcessingFailureTypeMapper

  /** Export this api for background executor, like WorkerManager */
  suspend fun runBackground(payload: FinishSendingFileWorkerPayload) {
    backgroundProcessingUseCase(payload, failureTypeMapper)
  }
}

data class SendUriUseCaseBaseDependencies
@Inject
constructor(
    val backgroundProcessingUseCase: BackgroundProcessingUseCase,
    val storageRepository: StorageRepository,
    val backgroundTaskRunner: BackgroundTaskRunner,
    val initializeAttachmentMessageUseCase: InitializeAttachmentMessageUseCase,
    val copyFileUseCase: CopyFileUseCase,
    val finishProcessingUseCase: FinishProcessingUseCase,
)

data class ResolveMetadataResult(
    val srcUriStr: UriStr,
    val userId: UserId,
    val topicId: TopicId,
    val messageTimestamp: Instant,
    val messageType: MessageType,
    val fileMetadata: FileMetadataUnion,
)

/**
 * Split the full process to 3 steps, to suite the UI requirements that sending multiple files ASAP
 *
 * 1. resolve metadata: fast, decide rendering messageType, resolve file metadata [can be parallel]
 * 2. insert initial message to db: fast. [in sequence to avoid ui jump]
 * 3. copy uri to internal, start background task. slower. [can be parallel]
 */
abstract class SendUriUseCaseBase(
    internal val dependencies: SendUriUseCaseBaseDependencies,
) : SendUseCaseBase(dependencies.backgroundProcessingUseCase, dependencies.storageRepository) {

  /**
   * get metadata, decide the rendering-message-type
   *
   * Run in IO threads for specific time-consuming parts. It's safe to run it in UI thread.
   *
   * @return null when failed
   */
  suspend fun resolveMetadata(
      srcUriStr: UriStr,
      userId: UserId,
      topicId: TopicId,
      messageTimestamp: Instant,
  ): ResolveMetadataResult? {
    val srcPath = AbsolutePathModel.UriStrModel(srcUriStr)
    val messageType = resolveMessageTypeForRender(srcPath)
    val fileMetadata =
        getMetadataOrNullBasedOnMessageType(
            dependencies.storageRepository,
            path = srcPath,
            messageType = messageType,
        )
            ?: run {
              Timber.w(
                  "Failed to get file metadata, $srcUriStr is invalid, " +
                      "action-msg-type=$messageTypeFromSendAction, render-msg-type=$messageType"
              )
              return null
            }
    return ResolveMetadataResult(
        srcUriStr = srcUriStr,
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
  suspend fun insertInitialMessage(
      resolveMetadataResult: ResolveMetadataResult,
  ): MessageId? {
    val messageId =
        try {
          dependencies.initializeAttachmentMessageUseCase.forUriSource(
              srcUriStr = resolveMetadataResult.srcUriStr,
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
  suspend fun copyToInternalAndStartBackgroundTask(
      resolveMetadataResult: ResolveMetadataResult,
      messageId: MessageId,
  ): Boolean {
    val copyInternalSuccessResult =
        when (
            val result =
                dependencies.copyFileUseCase.copySrcToInternalCacheAndUpdateState(
                    messageId = messageId,
                    userId = resolveMetadataResult.userId,
                    srcUriStr = resolveMetadataResult.srcUriStr,
                    messageTimestamp = resolveMetadataResult.messageTimestamp,
                    originalDisplayName = resolveMetadataResult.fileMetadata.displayName,
                )
        ) {
          is CopyStageResult.Failure -> {
            dependencies.finishProcessingUseCase.onFailure(
                messageId = messageId,
                stage = failureTypeMapper.mapCacheCopyFailure(result.type),
                errorUserRetryable = result.retryable,
            )
            return false
          }

          is CopyStageResult.Success -> result
        }

    dependencies.backgroundTaskRunner.finishSendingAttachmentMessage(
        FinishSendingFileWorkerPayload(
            messageId = messageId,
            userId = resolveMetadataResult.userId,
            topicId = resolveMetadataResult.topicId,
            messageTimestamp = resolveMetadataResult.messageTimestamp,
            fileMetadata = resolveMetadataResult.fileMetadata,
            cacheFilename = copyInternalSuccessResult.resultFilename,
            messageType = resolveMetadataResult.messageType,
            srcUriStr = resolveMetadataResult.srcUriStr,
        )
    )
    return true
  }

  suspend operator fun invoke(
      srcUriStr: UriStr,
      userId: UserId,
      topicId: TopicId,
      messageTimestamp: Instant,
  ): Boolean {
    val result =
        resolveMetadata(
            srcUriStr = srcUriStr,
            userId = userId,
            topicId = topicId,
            messageTimestamp = messageTimestamp,
        ) ?: return false
    val messageId = insertInitialMessage(result) ?: return false

    return copyToInternalAndStartBackgroundTask(result, messageId)
  }
}

/**
 * Dependencies for send cache file use case
 *
 * WHY not inherent from SendUriUseCaseBaseDependencies: more complicated both in code and semantic
 */
data class SendCacheFileUseCaseBaseDependencies
@Inject
constructor(
    val backgroundProcessingUseCase: BackgroundProcessingUseCase,
    val storagePathUseCase: StoragePathUseCase,
    val storageRepository: StorageRepository,
    val backgroundTaskRunner: BackgroundTaskRunner,
    val initializeAttachmentMessageUseCase: InitializeAttachmentMessageUseCase,
    val copyFileUseCase: CopyFileUseCase,
    val finishProcessingUseCase: FinishProcessingUseCase,
)

abstract class SendCacheFileUseCaseBase(
    val dependencies: SendCacheFileUseCaseBaseDependencies,
) : SendUseCaseBase(dependencies.backgroundProcessingUseCase, dependencies.storageRepository) {

  suspend operator fun invoke(
    cacheFilename: String,
    userId: UserId,
    topicId: TopicId,
    messageTimestamp: Instant,
  ): Boolean {
    val cacheFile =
        dependencies.storagePathUseCase.buildMessageCacheFileStoragePath(
            userId,
            cacheFilename,
        )
    val cacheAbsolutePath =
        dependencies.storageRepository
            .resolveStoragePathToAbsolutePathsWithoutCreating(cacheFile)
            .last()
    val messageType = resolveMessageTypeForRender(cacheAbsolutePath)
    val fileMetadata =
        getMetadataOrNullBasedOnMessageType(
            dependencies.storageRepository,
            path = cacheAbsolutePath,
            messageType = messageType,
        )
            ?: run {
              Timber.w(
                  "Failed to get file metadata, %s is invalid, message-type=%s",
                  "[<$cacheFilename> => <$cacheFile> => <$cacheAbsolutePath>]",
                  "[action=$messageTypeFromSendAction, render=$messageType]",
              )
              return false
            }
    val messageId =
        try {
          dependencies.initializeAttachmentMessageUseCase.forCacheFileSource(
              cacheFilename = cacheFilename,
              senderId = userId,
              topicId = topicId,
              messageTimestamp = messageTimestamp,
              messageType = messageType,
              fileMetadata = fileMetadata,
          )
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Timber.e(e, "Failed to insert initialized message, can't do anything")
          // can't do anything, just return...
          return false
        }
    dependencies.backgroundTaskRunner.finishSendingAttachmentMessage(
        FinishSendingFileWorkerPayload(
            messageId = messageId,
            userId = userId,
            topicId = topicId,
            messageTimestamp = messageTimestamp,
            messageType = messageType,
            srcUriStr = null,
            cacheFilename = cacheFilename,
            fileMetadata = fileMetadata,
        )
    )
    return true
  }
}

/**  */
private suspend fun getMetadataOrNullBasedOnMessageType(
    repository: StorageRepository,
    path: AbsolutePathModel,
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
