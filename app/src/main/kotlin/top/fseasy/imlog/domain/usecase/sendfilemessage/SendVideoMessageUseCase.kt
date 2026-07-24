package top.fseasy.imlog.domain.usecase.sendfilemessage

import top.fseasy.imlog.domain.model.AbsolutePathModel
import top.fseasy.imlog.domain.model.FileMetadataUnion
import top.fseasy.imlog.domain.model.VideoMessageProcessingErrorStage
import top.fseasy.imlog.domain.model.MessageType
import top.fseasy.imlog.domain.model.UriStr
import top.fseasy.imlog.domain.model.toMetadataUnion
import top.fseasy.imlog.domain.repository.StorageRepository
import top.fseasy.imlog.domain.usecase.sendfilemessage.stage.CopyStageFailureType
import top.fseasy.imlog.domain.usecase.sendfilemessage.stage.FinishProcessingStageFailureType
import top.fseasy.imlog.domain.usecase.sendfilemessage.stage.GenerateThumbnailStageFailureType
import javax.inject.Inject

class SendVideoMessageUseCase @Inject constructor(
    dependencies: SendUriUseCaseBaseDependencies,
) : SendUriUseCaseBase(dependencies) {
    override val messageTypeFromSendAction: MessageType
        get() = MessageType.Video

    override val failureTypeMapper: ProcessingFailureTypeMapper
        get() = videoProcessingFailureTypeMapper
}

internal val videoProcessingFailureTypeMapper = ProcessingFailureTypeMapper(
    mapCacheCopyFailure = { copyFailureType ->
        when (copyFailureType) {
            CopyStageFailureType.CopyFile -> VideoMessageProcessingErrorStage.CopySrcToInternalCache
            CopyStageFailureType.SaveFilenameToDb -> VideoMessageProcessingErrorStage.SetInternalFilenameToDb
            CopyStageFailureType.UpdateDbIllegalState -> VideoMessageProcessingErrorStage.IllegalState
        }
    },
    mapSharedStorageCopyFailure = { copyFailureType ->
        when (copyFailureType) {
            CopyStageFailureType.CopyFile -> VideoMessageProcessingErrorStage.CopyToSharedStorage
            CopyStageFailureType.SaveFilenameToDb -> VideoMessageProcessingErrorStage.SetRawFilenameToDb
            CopyStageFailureType.UpdateDbIllegalState -> VideoMessageProcessingErrorStage.IllegalState
        }
    },
    mapThumbnailFailure = { thumbnailFailureType ->
        when (thumbnailFailureType) {
            GenerateThumbnailStageFailureType.Generate -> VideoMessageProcessingErrorStage.GenerateThumbnail
            GenerateThumbnailStageFailureType.SaveFile -> VideoMessageProcessingErrorStage.SaveThumbnailFile
            GenerateThumbnailStageFailureType.SetFilenameToDb -> VideoMessageProcessingErrorStage.SetThumbnailFilenameToDb
            GenerateThumbnailStageFailureType.UpdateDbIllegalState -> VideoMessageProcessingErrorStage.IllegalState
        }
    },
    mapFinishTaskFailure = { finishFailureType ->
        when (finishFailureType) {
            FinishProcessingStageFailureType.DeleteCacheFile -> VideoMessageProcessingErrorStage.DeleteInternalFileCache
            FinishProcessingStageFailureType.DeleteTaskStateFromDb -> VideoMessageProcessingErrorStage.DeleteTaskStateFromDb
        }
    }
)