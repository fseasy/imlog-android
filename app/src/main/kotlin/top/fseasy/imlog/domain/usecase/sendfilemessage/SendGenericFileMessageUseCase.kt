package top.fseasy.imlog.domain.usecase.sendfilemessage

import top.fseasy.imlog.domain.model.GenericFileMessageProcessingErrorStage
import top.fseasy.imlog.domain.model.MessageType
import top.fseasy.imlog.domain.usecase.sendfilemessage.stage.CopyStageFailureType
import top.fseasy.imlog.domain.usecase.sendfilemessage.stage.FinishProcessingStageFailureType
import top.fseasy.imlog.domain.usecase.sendfilemessage.stage.GenerateThumbnailStageFailureType
import javax.inject.Inject

class SendGenericFileMessageUseCase @Inject constructor(
    dependencies: SendUriUseCaseBaseDependencies,
) : SendUriUseCaseBase(dependencies) {

    override val messageTypeFromSendAction: MessageType
        get() = MessageType.GenericFile

    override val failureTypeMapper: ProcessingFailureTypeMapper
        get() = genericFileProcessingFailureTypeMapper
}

internal val genericFileProcessingFailureTypeMapper =
    ProcessingFailureTypeMapper(mapCacheCopyFailure = { copyFailureType ->
        when (copyFailureType) {
            CopyStageFailureType.CopyFile -> GenericFileMessageProcessingErrorStage.CopySrcToInternalCache
            CopyStageFailureType.SaveFilenameToDb -> GenericFileMessageProcessingErrorStage.SetInternalFilenameToDb
            CopyStageFailureType.UpdateDbIllegalState -> GenericFileMessageProcessingErrorStage.IllegalState
        }
    }, mapSharedStorageCopyFailure = { copyFailureType ->
        when (copyFailureType) {
            CopyStageFailureType.CopyFile -> GenericFileMessageProcessingErrorStage.CopyToSharedStorage
            CopyStageFailureType.SaveFilenameToDb -> GenericFileMessageProcessingErrorStage.SetRawFilenameToDb
            CopyStageFailureType.UpdateDbIllegalState -> GenericFileMessageProcessingErrorStage.IllegalState
        }
    }, mapThumbnailFailure = { thumbnailFailureType ->
        when (thumbnailFailureType) {
            GenerateThumbnailStageFailureType.Generate -> GenericFileMessageProcessingErrorStage.GenerateThumbnail
            GenerateThumbnailStageFailureType.SaveFile -> GenericFileMessageProcessingErrorStage.SaveThumbnailFile
            GenerateThumbnailStageFailureType.SetFilenameToDb -> GenericFileMessageProcessingErrorStage.SetThumbnailFilenameToDb
            GenerateThumbnailStageFailureType.UpdateDbIllegalState -> GenericFileMessageProcessingErrorStage.IllegalState
        }
    }, mapFinishTaskFailure = { finishFailureType ->
        when (finishFailureType) {
            FinishProcessingStageFailureType.DeleteCacheFile -> GenericFileMessageProcessingErrorStage.DeleteInternalFileCache
            FinishProcessingStageFailureType.DeleteTaskStateFromDb -> GenericFileMessageProcessingErrorStage.DeleteTaskStateFromDb
        }
    })