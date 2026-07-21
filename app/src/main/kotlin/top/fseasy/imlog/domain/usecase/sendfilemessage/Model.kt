package top.fseasy.imlog.domain.usecase.sendfilemessage

import top.fseasy.imlog.domain.model.MessageProcessingErrorStage
import top.fseasy.imlog.domain.usecase.sendfilemessage.stage.CopyStageFailureType
import top.fseasy.imlog.domain.usecase.sendfilemessage.stage.FinishTaskFailureType
import top.fseasy.imlog.domain.usecase.sendfilemessage.stage.GenerateThumbnailStageFailureType

internal data class ProcessingFailureTypeMapper(
    val mapCacheRawCopyFailure: (CopyStageFailureType) -> MessageProcessingErrorStage,
    val mapRawPersistentRawCopyFailure: (CopyStageFailureType) -> MessageProcessingErrorStage,
    val mapThumbnailFailure: (GenerateThumbnailStageFailureType) -> MessageProcessingErrorStage,
    val mapFinishTaskFailure: (FinishTaskFailureType) -> MessageProcessingErrorStage,
)
