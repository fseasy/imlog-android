package top.fseasy.imlog.domain.usecase.sendattachment

import top.fseasy.imlog.domain.model.MessageType
import top.fseasy.imlog.domain.model.VideoMessageProcessingErrorStage
import top.fseasy.imlog.domain.usecase.sendattachment.stage.CopyStageFailureType
import top.fseasy.imlog.domain.usecase.sendattachment.stage.CleanupStageFailureType
import top.fseasy.imlog.domain.usecase.sendattachment.stage.GenerateThumbnailStageFailureType
import javax.inject.Inject

class SendVideoMessageUseCase
@Inject
constructor(
  dependencies: SendAttachmentDependencies,
) : SendAttachmentUseCaseBase(dependencies) {
  override val messageTypeFromSendAction: MessageType
    get() = MessageType.Video

  override val failureTypeMapper: ProcessingFailureTypeMapper
    get() = videoProcessingFailureTypeMapper
}

internal val videoProcessingFailureTypeMapper =
    ProcessingFailureTypeMapper(
        mapCacheCopyFailure = { copyFailureType ->
          when (copyFailureType) {
            CopyStageFailureType.CopyFile -> VideoMessageProcessingErrorStage.CopySrcToInternalCache
            CopyStageFailureType.SaveFilenameToDb ->
                VideoMessageProcessingErrorStage.SetInternalFilenameToDb
            CopyStageFailureType.UpdateDbIllegalState ->
                VideoMessageProcessingErrorStage.IllegalState
          }
        },
        mapSharedStorageCopyFailure = { copyFailureType ->
          when (copyFailureType) {
            CopyStageFailureType.CopyFile -> VideoMessageProcessingErrorStage.CopyToSharedStorage
            CopyStageFailureType.SaveFilenameToDb ->
                VideoMessageProcessingErrorStage.SetRawFilenameToDb
            CopyStageFailureType.UpdateDbIllegalState ->
                VideoMessageProcessingErrorStage.IllegalState
          }
        },
        mapThumbnailFailure = { thumbnailFailureType ->
          when (thumbnailFailureType) {
            GenerateThumbnailStageFailureType.Generate ->
                VideoMessageProcessingErrorStage.GenerateThumbnail
            GenerateThumbnailStageFailureType.SaveFile ->
                VideoMessageProcessingErrorStage.SaveThumbnailFile
            GenerateThumbnailStageFailureType.SetFilenameToDb ->
                VideoMessageProcessingErrorStage.SetThumbnailFilenameToDb
            GenerateThumbnailStageFailureType.UpdateDbIllegalState ->
                VideoMessageProcessingErrorStage.IllegalState
          }
        },
        mapFinishTaskFailure = { finishFailureType ->
          when (finishFailureType) {
            CleanupStageFailureType.DeleteCacheFile ->
                VideoMessageProcessingErrorStage.DeleteInternalFileCache
            CleanupStageFailureType.DeleteTaskStateFromDb ->
                VideoMessageProcessingErrorStage.DeleteTaskStateFromDb
          }
        },
    )
