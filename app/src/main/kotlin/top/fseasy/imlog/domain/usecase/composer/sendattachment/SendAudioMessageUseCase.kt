package top.fseasy.imlog.domain.usecase.composer.sendattachment

import top.fseasy.imlog.domain.model.AudioMessageProcessingErrorStage
import top.fseasy.imlog.domain.model.MessageType
import top.fseasy.imlog.domain.usecase.composer.sendattachment.stage.CopyStageFailureType
import top.fseasy.imlog.domain.usecase.composer.sendattachment.stage.CleanupStageFailureType
import javax.inject.Inject

class SendAudioMessageUseCase
@Inject
constructor(
  dependencies: SendAttachmentDependencies,
) : SendAttachmentUseCaseBase(dependencies = dependencies) {
  override val messageTypeFromSendAction: MessageType
    get() = MessageType.Audio

  override val failureTypeMapper: ProcessingFailureTypeMapper
    get() = audioProcessingFailureTypeMapper
}

internal val audioProcessingFailureTypeMapper =
    ProcessingFailureTypeMapper(
        mapCacheCopyFailure = { copyFailureType ->
          when (copyFailureType) {
            CopyStageFailureType.CopyFile -> AudioMessageProcessingErrorStage.CopySrcToInternalCache
            CopyStageFailureType.SaveFilenameToDb ->
                AudioMessageProcessingErrorStage.SetInternalFilenameToDb
            CopyStageFailureType.UpdateDbIllegalState ->
                AudioMessageProcessingErrorStage.IllegalState
          }
        },
        mapSharedStorageCopyFailure = { copyFailureType ->
          when (copyFailureType) {
            CopyStageFailureType.CopyFile -> AudioMessageProcessingErrorStage.CopyToSharedStorage
            CopyStageFailureType.SaveFilenameToDb ->
                AudioMessageProcessingErrorStage.SetRawFilenameToDb
            CopyStageFailureType.UpdateDbIllegalState ->
                AudioMessageProcessingErrorStage.IllegalState
          }
        },
        mapThumbnailFailure = { error("Audio message don't have generate-thumbnail stage") },
        mapFinishTaskFailure = { finishFailureType ->
          when (finishFailureType) {
            CleanupStageFailureType.DeleteCacheFile ->
                AudioMessageProcessingErrorStage.DeleteInternalFileCache
            CleanupStageFailureType.DeleteTaskStateFromDb ->
                AudioMessageProcessingErrorStage.DeleteTaskStateFromDb
          }
        },
    )
