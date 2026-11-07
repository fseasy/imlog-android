package top.fseasy.imlog.domain.usecase.sendattachment

import javax.inject.Inject
import top.fseasy.imlog.domain.model.MessageType
import top.fseasy.imlog.domain.model.StoragePathModel
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.model.VoiceMessageProcessingErrorStage
import top.fseasy.imlog.domain.usecase.sendattachment.stage.CleanupStageFailureType
import top.fseasy.imlog.domain.usecase.sendattachment.stage.CopyStageFailureType

class SendVoiceMessageUseCase
@Inject
constructor(
    dependencies: SendAttachmentDependencies,
    private val attachmentPathUseCase: AttachmentPathUseCase,
) : SendAttachmentUseCaseBase(dependencies) {
  override val messageTypeFromSendAction: MessageType
    get() = MessageType.Voice

  override val failureTypeMapper: ProcessingFailureTypeMapper
    get() = voiceProcessingFailureTypeMapper

  fun newVoiceDraftPath(
    userId: UserId,
    messageTimestamp: kotlin.time.Instant,
    audioFileExtension: String,
  ): StoragePathModel.Internal {
    val filename =
        attachmentPathUseCase.generateInternalFilename(messageTimestamp, "voice${audioFileExtension}")
    return attachmentPathUseCase.generateInternalCachePath(userId, filename)
  }

  fun restoreDraftPath(userId: UserId, filename: String) =
      attachmentPathUseCase.generateInternalCachePath(userId, filename)
}

internal val voiceProcessingFailureTypeMapper =
    ProcessingFailureTypeMapper(
        mapCacheCopyFailure = { error("No cache copy stage in voice processing") },
        mapSharedStorageCopyFailure = { copyFailureType ->
          when (copyFailureType) {
            CopyStageFailureType.CopyFile -> VoiceMessageProcessingErrorStage.CopyToSharedStorage
            CopyStageFailureType.SaveFilenameToDb ->
                VoiceMessageProcessingErrorStage.SetRawFilenameToDb
            CopyStageFailureType.UpdateDbIllegalState ->
                VoiceMessageProcessingErrorStage.IllegalState
          }
        },
        mapThumbnailFailure = { error("No generate-thumbnail stage in voice processing") },
        mapFinishTaskFailure = { finishFailureType ->
          when (finishFailureType) {
            CleanupStageFailureType.DeleteCacheFile ->
                VoiceMessageProcessingErrorStage.DeleteInternalFileCache
            CleanupStageFailureType.DeleteTaskStateFromDb ->
                VoiceMessageProcessingErrorStage.DeleteTaskStateFromDb
          }
        },
    )
