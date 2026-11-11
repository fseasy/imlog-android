package top.fseasy.imlog.domain.usecase.composer.sendattachment

import top.fseasy.imlog.domain.model.ImageMessageProcessingErrorStage
import top.fseasy.imlog.domain.model.MessageType
import top.fseasy.imlog.domain.usecase.composer.sendattachment.stage.CopyStageFailureType
import top.fseasy.imlog.domain.usecase.composer.sendattachment.stage.CleanupStageFailureType
import top.fseasy.imlog.domain.usecase.composer.sendattachment.stage.GenerateThumbnailStageFailureType
import javax.inject.Inject

class SendImageMessageUseCase
@Inject
constructor(
  dependencies: SendAttachmentDependencies,
) : SendAttachmentUseCaseBase(dependencies) {
  override val messageTypeFromSendAction: MessageType
    get() = MessageType.Image

  override val failureTypeMapper: ProcessingFailureTypeMapper
    get() = imageProcessingFailureTypeMapper
}

internal val imageProcessingFailureTypeMapper =
    ProcessingFailureTypeMapper(
        mapCacheCopyFailure = { copyFailureType ->
          when (copyFailureType) {
            CopyStageFailureType.CopyFile -> ImageMessageProcessingErrorStage.CopySrcToInternalCache
            CopyStageFailureType.SaveFilenameToDb ->
                ImageMessageProcessingErrorStage.SetInternalFilenameToDb
            CopyStageFailureType.UpdateDbIllegalState ->
                ImageMessageProcessingErrorStage.IllegalState
          }
        },
        mapSharedStorageCopyFailure = { copyFailureType ->
          when (copyFailureType) {
            CopyStageFailureType.CopyFile -> ImageMessageProcessingErrorStage.CopyToSharedStorage
            CopyStageFailureType.SaveFilenameToDb ->
                ImageMessageProcessingErrorStage.SetRawFilenameToDb
            CopyStageFailureType.UpdateDbIllegalState ->
                ImageMessageProcessingErrorStage.IllegalState
          }
        },
        mapThumbnailFailure = { thumbnailFailureType ->
          when (thumbnailFailureType) {
            GenerateThumbnailStageFailureType.Generate ->
                ImageMessageProcessingErrorStage.GenerateThumbnail
            GenerateThumbnailStageFailureType.SaveFile ->
                ImageMessageProcessingErrorStage.SaveThumbnailFile
            GenerateThumbnailStageFailureType.SetFilenameToDb ->
                ImageMessageProcessingErrorStage.SetThumbnailFilenameToDb
            GenerateThumbnailStageFailureType.UpdateDbIllegalState ->
                ImageMessageProcessingErrorStage.IllegalState
          }
        },
        mapFinishTaskFailure = { finishFailureType ->
          when (finishFailureType) {
            CleanupStageFailureType.DeleteCacheFile ->
                ImageMessageProcessingErrorStage.DeleteInternalFileCache
            CleanupStageFailureType.DeleteTaskStateFromDb ->
                ImageMessageProcessingErrorStage.DeleteTaskStateFromDb
          }
        },
    )
