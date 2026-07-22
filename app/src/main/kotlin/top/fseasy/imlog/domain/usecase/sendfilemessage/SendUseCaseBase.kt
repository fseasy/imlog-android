package top.fseasy.imlog.domain.usecase.sendfilemessage

import kotlinx.coroutines.CancellationException
import timber.log.Timber
import top.fseasy.imlog.domain.model.FileMetadataUnion
import top.fseasy.imlog.domain.model.FinishFileSendingWorkerPayload
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

abstract class SendSupportingBackgroundBase(
    private val backgroundProcessingUseCase: BackgroundProcessingUseCase,
) {

    internal abstract val failureTypeMapper: ProcessingFailureTypeMapper

    /**
     * Export this api for background executor, like WorkerManager
     */
    suspend fun runBackground(payload: FinishFileSendingWorkerPayload) {
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
    private val dependencies: SendUriUseCaseBaseDependencies,
) : SendSupportingBackgroundBase(dependencies.backgroundProcessingUseCase) {

    protected abstract val messageType: MessageType
    protected abstract suspend fun getMetadataOrNull(
        storageRepository: StorageRepository,
        srcUriStr: UriStr,
    ): FileMetadataUnion?

    suspend operator fun invoke(
        srcUriStr: UriStr,
        userId: UserId,
        topicId: TopicId,
        messageTimestampMs: Long,
    ): Boolean {
        // 1. initialize the file message record to db
        val fileMetadata = getMetadataOrNull(dependencies.storageRepository, srcUriStr) ?: run {
            Timber.w("Failed to get audio metadata, $srcUriStr is invalid")
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
            FinishFileSendingWorkerPayload(
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
) : SendSupportingBackgroundBase(dependencies.backgroundProcessingUseCase) {

    protected abstract val messageType: MessageType
    protected abstract suspend fun getMetadataOrNull(
        storageRepository: StorageRepository,
        cacheFile: StoragePathModel.InternalOnly,
    ): FileMetadataUnion?

    suspend operator fun invoke(
        cacheFilename: String,
        userId: UserId,
        topicId: TopicId,
        messageTimestampMs: Long,
    ): Boolean {
        val cacheFile = dependencies.storagePathUseCase.buildMessageCacheFileStoragePath(
            userId, cacheFilename
        )
        val fileMetadata = getMetadataOrNull(dependencies.storageRepository, cacheFile) ?: run {
            Timber.w("Failed to get ${messageType.value} metadata, <$cacheFilename> => <$cacheFile> is invalid file")
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
            FinishFileSendingWorkerPayload(
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