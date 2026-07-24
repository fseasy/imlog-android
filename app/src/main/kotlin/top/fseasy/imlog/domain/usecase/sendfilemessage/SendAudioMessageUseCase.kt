package top.fseasy.imlog.domain.usecase.sendfilemessage

import top.fseasy.imlog.domain.model.AbsolutePathModel
import top.fseasy.imlog.domain.model.FileMetadataUnion
import top.fseasy.imlog.domain.model.AudioMessageProcessingErrorStage
import top.fseasy.imlog.domain.model.MessageType
import top.fseasy.imlog.domain.model.UriStr
import top.fseasy.imlog.domain.model.toMetadataUnion
import top.fseasy.imlog.domain.repository.StorageRepository
import top.fseasy.imlog.domain.usecase.sendfilemessage.stage.CopyStageFailureType
import top.fseasy.imlog.domain.usecase.sendfilemessage.stage.FinishProcessingStageFailureType
import javax.inject.Inject

class SendAudioMessageUseCase @Inject constructor(
    dependencies: SendUriUseCaseBaseDependencies,
) : SendUriUseCaseBase(dependencies = dependencies) {
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
                CopyStageFailureType.SaveFilenameToDb -> AudioMessageProcessingErrorStage.SetInternalFilenameToDb
                CopyStageFailureType.UpdateDbIllegalState -> AudioMessageProcessingErrorStage.IllegalState
            }
        },
        mapSharedStorageCopyFailure = { copyFailureType ->
            when (copyFailureType) {
                CopyStageFailureType.CopyFile -> AudioMessageProcessingErrorStage.CopyToSharedStorage
                CopyStageFailureType.SaveFilenameToDb -> AudioMessageProcessingErrorStage.SetRawFilenameToDb
                CopyStageFailureType.UpdateDbIllegalState -> AudioMessageProcessingErrorStage.IllegalState
            }
        },
        mapThumbnailFailure = { error("Audio message don't have generate-thumbnail stage") },
        mapFinishTaskFailure = { finishFailureType ->
            when (finishFailureType) {
                FinishProcessingStageFailureType.DeleteCacheFile -> AudioMessageProcessingErrorStage.DeleteInternalFileCache
                FinishProcessingStageFailureType.DeleteTaskStateFromDb -> AudioMessageProcessingErrorStage.DeleteTaskStateFromDb
            }
        })