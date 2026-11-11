package top.fseasy.imlog.domain.usecase.composer.sendattachment

import top.fseasy.imlog.domain.model.MessageProcessingErrorStage
import top.fseasy.imlog.domain.usecase.composer.sendattachment.stage.CopyStageFailureType
import top.fseasy.imlog.domain.usecase.composer.sendattachment.stage.CleanupStageFailureType
import top.fseasy.imlog.domain.usecase.composer.sendattachment.stage.GenerateThumbnailStageFailureType

internal data class ProcessingFailureTypeMapper(
  val mapCacheCopyFailure: (CopyStageFailureType) -> MessageProcessingErrorStage,
  val mapSharedStorageCopyFailure: (CopyStageFailureType) -> MessageProcessingErrorStage,
  val mapThumbnailFailure: (GenerateThumbnailStageFailureType) -> MessageProcessingErrorStage,
  val mapFinishTaskFailure: (CleanupStageFailureType) -> MessageProcessingErrorStage,
)
