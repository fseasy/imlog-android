package top.fseasy.imlog.domain.usecase.sendfilemessage

import kotlinx.coroutines.CancellationException
import timber.log.Timber
import top.fseasy.imlog.domain.model.AbsolutePathModel
import top.fseasy.imlog.domain.model.FileMetadataUnion
import top.fseasy.imlog.domain.model.FinishSendingFileWorkerPayload
import top.fseasy.imlog.domain.model.MessageType
import top.fseasy.imlog.domain.model.StoragePathModel
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UriStr
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.model.toMetadataUnion
import top.fseasy.imlog.domain.repository.BackgroundTaskRunner
import top.fseasy.imlog.domain.repository.StorageRepository
import top.fseasy.imlog.domain.usecase.StoragePathUseCase
import top.fseasy.imlog.domain.usecase.sendfilemessage.stage.CopyFileUseCase
import top.fseasy.imlog.domain.usecase.sendfilemessage.stage.CopyStageResult
import top.fseasy.imlog.domain.usecase.sendfilemessage.stage.FinishProcessingUseCase
import top.fseasy.imlog.domain.usecase.sendfilemessage.stage.InitializeFileMessageUseCase
import javax.inject.Inject

abstract class SendUseCaseBase(
    private val backgroundProcessingUseCase: BackgroundProcessingUseCase,
    private val storageRepository: StorageRepository,
) {
    protected abstract val messageTypeFromSendAction: MessageType

    /**
     * Why: 1. We want to set the messageType to the real actual file for GenericFile.
     *      2. this is the easist way to unifie the subclasses.
     * */
    protected suspend fun resolveMessageTypeForRender(srcPath: AbsolutePathModel): MessageType =
        when (messageTypeFromSendAction) {
            MessageType.GenericFile -> {
                storageRepository.getMimetypeOrNull(srcPath)
                    ?.let {
                        fileMimeTypeToMessageType(it)
                    } ?: MessageType.GenericFile
            }

            else -> messageTypeFromSendAction
        }


    internal abstract val failureTypeMapper: ProcessingFailureTypeMapper

    /**
     * Export this api for background executor, like WorkerManager
     */
    suspend fun runBackground(payload: FinishSendingFileWorkerPayload) {
        backgroundProcessingUseCase(payload, failureTypeMapper)
    }
}

data class SendUriUseCaseBaseDependencies @Inject constructor(
    val backgroundProcessingUseCase: BackgroundProcessingUseCase,
    val storageRepository: StorageRepository,
    val backgroundTaskRunner: BackgroundTaskRunner,
    val initializeFileMessageUseCase: InitializeFileMessageUseCase,
    val copyFileUseCase: CopyFileUseCase,
    val finishProcessingUseCase: FinishProcessingUseCase,
)

abstract class SendUriUseCaseBase(
    internal val dependencies: SendUriUseCaseBaseDependencies,
) : SendUseCaseBase(dependencies.backgroundProcessingUseCase, dependencies.storageRepository) {

    suspend operator fun invoke(
        srcUriStr: UriStr,
        userId: UserId,
        topicId: TopicId,
        messageTimestampMs: Long,
    ): Boolean {
        // 1. initialize the file message record to db
        val srcPath = AbsolutePathModel.UriStrModel(srcUriStr)
        val messageType = resolveMessageTypeForRender(srcPath)
        val fileMetadata = getMetadataOrNullBasedOnMessageType(
            dependencies.storageRepository,
            path = srcPath,
            messageType = messageType
        ) ?: run {
            Timber.w(
                "Failed to get file metadata, $srcUriStr is invalid, " +
                    "action-msg-type=$messageTypeFromSendAction, render-msg-type=$messageType"
            )
            return false
        }
        val messageId = try {
            dependencies.initializeFileMessageUseCase.forUriSource(
                srcUriStr = srcUriStr,
                senderId = userId,
                topicId = topicId,
                messageTimestampMs = messageTimestampMs,
                messageType = messageType,
                fileMetadata = fileMetadata
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize file message, can't do anything")
            // can't do anything, just return...
            return false
        }

        // 2. copy src file to internal cache, then update status
        val copyInternalSuccessResult =
            when (val result = dependencies.copyFileUseCase.copySrcToInternalCacheAndUpdateState(
                messageId = messageId,
                userId = userId,
                srcUriStr = srcUriStr,
                messageTimestampMs = messageTimestampMs,
                originalDisplayName = fileMetadata.displayName,
            )) {
                is CopyStageResult.Failure -> {
                    dependencies.finishProcessingUseCase.onFailure(
                        messageId = messageId,
                        stage = failureTypeMapper.mapCacheCopyFailure(result.type),
                        errorUserRetryable = result.retryable
                    )
                    return false
                }

                is CopyStageResult.Success -> result
            }

        dependencies.backgroundTaskRunner.finishSendingFileMessage(
            FinishSendingFileWorkerPayload(
                messageId = messageId,
                userId = userId,
                topicId = topicId,
                messageTimestampMs = messageTimestampMs,
                fileMetadata = fileMetadata,
                cacheFilename = copyInternalSuccessResult.resultFilename,
                messageType = messageType,
                srcUriStr = srcUriStr
            )
        )
        return true
    }
}


/**
 * Dependencies for send cache file use case
 *
 * WHY not inherent from SendUriUseCaseBaseDependencies: more complicated both in code and semantic
 */
data class SendCacheFileUseCaseBaseDependencies @Inject constructor(
    val backgroundProcessingUseCase: BackgroundProcessingUseCase,
    val storagePathUseCase: StoragePathUseCase,
    val storageRepository: StorageRepository,
    val backgroundTaskRunner: BackgroundTaskRunner,
    val initializeFileMessageUseCase: InitializeFileMessageUseCase,
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
        messageTimestampMs: Long,
    ): Boolean {
        val cacheFile = dependencies.storagePathUseCase.buildMessageCacheFileStoragePath(
            userId, cacheFilename
        )
        val cacheAbsolutePath =
            dependencies.storageRepository.resolveStoragePathToAbsolutePathsWithoutCreating(
                cacheFile
            )
                .last()
        val messageType = resolveMessageTypeForRender(cacheAbsolutePath)
        val fileMetadata = getMetadataOrNullBasedOnMessageType(
            dependencies.storageRepository,
            path = cacheAbsolutePath,
            messageType = messageType
        ) ?: run {
            Timber.w(
                "Failed to get file metadata, %s is invalid, message-type=%s",
                "[<$cacheFilename> => <$cacheFile> => <$cacheAbsolutePath>]",
                "[action=$messageTypeFromSendAction, render=$messageType]"
            )
            return false
        }
        val messageId = try {
            dependencies.initializeFileMessageUseCase.forCacheFileSource(
                cacheFilename = cacheFilename,
                senderId = userId,
                topicId = topicId,
                messageTimestampMs = messageTimestampMs,
                messageType = messageType,
                fileMetadata = fileMetadata
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to insert initialized message, can't do anything")
            // can't do anything, just return...
            return false
        }
        dependencies.backgroundTaskRunner.finishSendingFileMessage(
            FinishSendingFileWorkerPayload(
                messageId = messageId,
                userId = userId,
                topicId = topicId,
                messageTimestampMs = messageTimestampMs,
                messageType = messageType,
                srcUriStr = null,
                cacheFilename = cacheFilename,
                fileMetadata = fileMetadata,
            )
        )
        return true
    }
}

/**
 * Helper function to map mimetype to MessageType. Mainly for GenericFile
 */
private fun fileMimeTypeToMessageType(mimeType: String): MessageType =
    if (mimeType.startsWith("video")) {
        MessageType.Video
    } else if (mimeType.startsWith("audio")) {
        MessageType.Audio
    } else if (mimeType.startsWith("image")) {
        MessageType.Image
    } else MessageType.GenericFile

/**
 *
 */
private suspend fun getMetadataOrNullBasedOnMessageType(
    repository: StorageRepository,
    path: AbsolutePathModel,
    messageType: MessageType,
): FileMetadataUnion? =
    when (messageType) {
        MessageType.Image -> repository.getImageMetadataOrNull(path)
            ?.toMetadataUnion()

        MessageType.Video -> repository.getVideoMetadataOrNull(path)
            ?.toMetadataUnion()

        MessageType.Audio,
        MessageType.Voice,
            -> repository.getAudioMetadataOrNull(path)
            ?.toMetadataUnion()

        MessageType.GenericFile -> repository.getGenericFileMetadataOrNull(path)
            ?.toMetadataUnion()

        MessageType.Text -> error("Text MessageType shouldn't use this UseCase")
    }
