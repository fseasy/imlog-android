package top.fseasy.imlog.domain.usecase.sendfilemessage

import top.fseasy.imlog.domain.model.FileMetadataUnion
import top.fseasy.imlog.domain.model.MessageType
import top.fseasy.imlog.domain.model.VoiceMessageProcessingErrorStage
import top.fseasy.imlog.domain.model.StoragePathModel
import top.fseasy.imlog.domain.model.toMetadataUnion
import top.fseasy.imlog.domain.repository.StorageRepository
import top.fseasy.imlog.domain.usecase.sendfilemessage.stage.CopyStageFailureType
import top.fseasy.imlog.domain.usecase.sendfilemessage.stage.FinishProcessingStageFailureType
import javax.inject.Inject

class SendVoiceMessageUseCase @Inject constructor(dependencies: SendCacheFileUseCaseBaseDependencies) :
    SendCacheFileUseCaseBase(dependencies) {
    override val messageTypeFromSendAction: MessageType
        get() = MessageType.Voice

    override val failureTypeMapper: ProcessingFailureTypeMapper
        get() = voiceProcessingFailureTypeMapper
}

internal val voiceProcessingFailureTypeMapper =
    ProcessingFailureTypeMapper(
        mapCacheCopyFailure = { error("No cache copy stage in voice processing") },
        mapSharedStorageCopyFailure = { copyFailureType ->
            when (copyFailureType) {
                CopyStageFailureType.CopyFile -> VoiceMessageProcessingErrorStage.CopyToSharedStorage
                CopyStageFailureType.SaveFilenameToDb -> VoiceMessageProcessingErrorStage.SetRawFilenameToDb
                CopyStageFailureType.UpdateDbIllegalState -> VoiceMessageProcessingErrorStage.IllegalState
            }
        },
        mapThumbnailFailure = { error("No generate-thumbnail stage in voice processing") },
        mapFinishTaskFailure = { finishFailureType ->
            when (finishFailureType) {
                FinishProcessingStageFailureType.DeleteCacheFile -> VoiceMessageProcessingErrorStage.DeleteInternalFileCache
                FinishProcessingStageFailureType.DeleteTaskStateFromDb -> VoiceMessageProcessingErrorStage.DeleteTaskStateFromDb
            }
        })