package top.fseasy.imlog.domain.usecase.sendfilemessage

import top.fseasy.imlog.domain.model.MessageProcessingErrorStage
import top.fseasy.imlog.domain.usecase.sendfilemessage.stage.CopyStageFailureType
import top.fseasy.imlog.domain.usecase.sendfilemessage.stage.FinishProcessingStageFailureType
import top.fseasy.imlog.domain.usecase.sendfilemessage.stage.GenerateThumbnailStageFailureType

internal data class ProcessingFailureTypeMapper(
    val mapCacheCopyFailure: (CopyStageFailureType) -> MessageProcessingErrorStage,
    val mapSharedStorageCopyFailure: (CopyStageFailureType) -> MessageProcessingErrorStage,
    val mapThumbnailFailure: (GenerateThumbnailStageFailureType) -> MessageProcessingErrorStage,
    val mapFinishTaskFailure: (FinishProcessingStageFailureType) -> MessageProcessingErrorStage,
)
