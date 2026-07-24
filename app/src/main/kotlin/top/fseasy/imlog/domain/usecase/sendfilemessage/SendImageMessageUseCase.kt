package top.fseasy.imlog.domain.usecase.sendfilemessage

import top.fseasy.imlog.domain.model.AbsolutePathModel
import top.fseasy.imlog.domain.model.FileMetadataUnion
import top.fseasy.imlog.domain.model.ImageMessageProcessingErrorStage
import top.fseasy.imlog.domain.model.MessageType
import top.fseasy.imlog.domain.model.UriStr
import top.fseasy.imlog.domain.model.toMetadataUnion
import top.fseasy.imlog.domain.repository.StorageRepository
import top.fseasy.imlog.domain.usecase.sendfilemessage.stage.CopyStageFailureType
import top.fseasy.imlog.domain.usecase.sendfilemessage.stage.FinishProcessingStageFailureType
import top.fseasy.imlog.domain.usecase.sendfilemessage.stage.GenerateThumbnailStageFailureType
import javax.inject.Inject

class SendImageMessageUseCase @Inject constructor(
    dependencies: SendUriUseCaseBaseDependencies,
) : SendUriUseCaseBase(dependencies) {
    override val messageTypeFromSendAction: MessageType
        get() = MessageType.Image

    override val failureTypeMapper: ProcessingFailureTypeMapper
        get() = imageProcessingFailureTypeMapper
}

internal val imageProcessingFailureTypeMapper = ProcessingFailureTypeMapper(
    mapCacheCopyFailure = { copyFailureType ->
        when (copyFailureType) {
            CopyStageFailureType.CopyFile -> ImageMessageProcessingErrorStage.CopySrcToInternalCache
            CopyStageFailureType.SaveFilenameToDb -> ImageMessageProcessingErrorStage.SetInternalFilenameToDb
            CopyStageFailureType.UpdateDbIllegalState -> ImageMessageProcessingErrorStage.IllegalState
        }
    },
    mapSharedStorageCopyFailure = { copyFailureType ->
        when (copyFailureType) {
            CopyStageFailureType.CopyFile -> ImageMessageProcessingErrorStage.CopyToSharedStorage
            CopyStageFailureType.SaveFilenameToDb -> ImageMessageProcessingErrorStage.SetRawFilenameToDb
            CopyStageFailureType.UpdateDbIllegalState -> ImageMessageProcessingErrorStage.IllegalState
        }
    },
    mapThumbnailFailure = { thumbnailFailureType ->
        when (thumbnailFailureType) {
            GenerateThumbnailStageFailureType.Generate -> ImageMessageProcessingErrorStage.GenerateThumbnail
            GenerateThumbnailStageFailureType.SaveFile -> ImageMessageProcessingErrorStage.SaveThumbnailFile
            GenerateThumbnailStageFailureType.SetFilenameToDb -> ImageMessageProcessingErrorStage.SetThumbnailFilenameToDb
            GenerateThumbnailStageFailureType.UpdateDbIllegalState -> ImageMessageProcessingErrorStage.IllegalState
        }
    },
    mapFinishTaskFailure = { finishFailureType ->
        when (finishFailureType) {
            FinishProcessingStageFailureType.DeleteCacheFile -> ImageMessageProcessingErrorStage.DeleteInternalFileCache
            FinishProcessingStageFailureType.DeleteTaskStateFromDb -> ImageMessageProcessingErrorStage.DeleteTaskStateFromDb
        }
    }
)