package top.fseasy.imlog.domain.usecase.sendattachment

import top.fseasy.imlog.domain.model.MessageProcessingErrorStage
import top.fseasy.imlog.domain.usecase.sendattachment.stage.CopyStageFailureType
import top.fseasy.imlog.domain.usecase.sendattachment.stage.FinishProcessingStageFailureType
import top.fseasy.imlog.domain.usecase.sendattachment.stage.GenerateThumbnailStageFailureType

internal data class ProcessingFailureTypeMapper(
    val mapCacheCopyFailure: (CopyStageFailureType) -> MessageProcessingErrorStage,
    val mapSharedStorageCopyFailure: (CopyStageFailureType) -> MessageProcessingErrorStage,
    val mapThumbnailFailure: (GenerateThumbnailStageFailureType) -> MessageProcessingErrorStage,
    val mapFinishTaskFailure: (FinishProcessingStageFailureType) -> MessageProcessingErrorStage,
)
