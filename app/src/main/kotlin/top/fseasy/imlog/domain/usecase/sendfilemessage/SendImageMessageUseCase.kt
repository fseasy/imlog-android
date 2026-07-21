package top.fseasy.imlog.domain.usecase.sendfilemessage

import timber.log.Timber
import top.fseasy.imlog.domain.model.AbsolutePathModel
import top.fseasy.imlog.domain.model.FinishFileSendingWorkerPayload
import top.fseasy.imlog.domain.model.MessageImageProcessingErrorStage
import top.fseasy.imlog.domain.model.MessageType
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UriStr
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.model.toMetadataUnion
import top.fseasy.imlog.domain.repository.StorageRepository
import top.fseasy.imlog.domain.repository.WorkerRunner
import top.fseasy.imlog.domain.usecase.StoragePathUseCase
import top.fseasy.imlog.domain.usecase.sendfilemessage.stage.CopyFileUseCase
import top.fseasy.imlog.domain.usecase.sendfilemessage.stage.CopyStageFailureType
import top.fseasy.imlog.domain.usecase.sendfilemessage.stage.CopyStageResult
import top.fseasy.imlog.domain.usecase.sendfilemessage.stage.FinishProcessingUseCase
import top.fseasy.imlog.domain.usecase.sendfilemessage.stage.FinishTaskFailureType
import top.fseasy.imlog.domain.usecase.sendfilemessage.stage.GenerateThumbnailStageFailureType
import top.fseasy.imlog.domain.usecase.sendfilemessage.stage.InitializeFileMessageUseCase
import javax.inject.Inject

class SendImageMessageUseCase @Inject constructor(
    private val storageRepository: StorageRepository,
    private val workerRunner: WorkerRunner,
    private val initializeFileMessageUseCase: InitializeFileMessageUseCase,
    private val copyFileUseCase: CopyFileUseCase,
    private val finishProcessingUseCase: FinishProcessingUseCase,
) {

    suspend fun invoke(
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
            initializeFileMessageUseCase.initializeUriSourceFileMessage(
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
        val copyInternalSuccessResult =
            when (val result = copyFileUseCase.copySrcToInternalCacheAndUpdateState(
                messageId = messageId,
                userId = userId,
                srcUriStr = srcUriStr,
                messageTimestampMs = messageTimestampMs,
                originalDisplayName = fileMetadata.displayName,
            )) {
                is CopyStageResult.Failure -> return finishProcessingUseCase.finishOnFailure(
                    messageId = messageId,
                    stage = imageProcessingFailureTypeMapper.mapCacheRawCopyFailure(result.type),
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
}

internal val imageProcessingFailureTypeMapper = ProcessingFailureTypeMapper(
    mapCacheRawCopyFailure = { copyFailureType ->
        when (copyFailureType) {
            CopyStageFailureType.CopyFile -> MessageImageProcessingErrorStage.CopySrcToInternalCache
            CopyStageFailureType.SaveFilenameToDb -> MessageImageProcessingErrorStage.SetInternalFilenameToDb
            CopyStageFailureType.UpdateDbIllegalState -> MessageImageProcessingErrorStage.IllegalState
        }
    },
    mapRawPersistentRawCopyFailure = { copyFailureType ->
        when (copyFailureType) {
            CopyStageFailureType.CopyFile -> MessageImageProcessingErrorStage.CopyToSharedStorage
            CopyStageFailureType.SaveFilenameToDb -> MessageImageProcessingErrorStage.SetRawFilenameToDb
            CopyStageFailureType.UpdateDbIllegalState -> MessageImageProcessingErrorStage.IllegalState
        }
    },
    mapThumbnailFailure = { thumbnailFailureType ->
        when (thumbnailFailureType) {
            GenerateThumbnailStageFailureType.Generate -> MessageImageProcessingErrorStage.GenerateThumbnail
            GenerateThumbnailStageFailureType.SaveFile -> MessageImageProcessingErrorStage.SaveThumbnailFile
            GenerateThumbnailStageFailureType.SetFilenameToDb -> MessageImageProcessingErrorStage.SetThumbnailFilenameToDb
            GenerateThumbnailStageFailureType.UpdateDbIllegalState -> MessageImageProcessingErrorStage.IllegalState
        }
    },
    mapFinishTaskFailure = { finishFailureType ->
        when (finishFailureType) {
            FinishTaskFailureType.DeleteCacheFile -> MessageImageProcessingErrorStage.DeleteInternalFileCache
            FinishTaskFailureType.DeleteTaskStateFromDb -> MessageImageProcessingErrorStage.DeleteTaskStateFromDb
        }
    }
)