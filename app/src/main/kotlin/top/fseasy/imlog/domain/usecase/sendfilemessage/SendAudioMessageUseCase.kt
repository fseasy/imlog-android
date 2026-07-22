package top.fseasy.imlog.domain.usecase.sendfilemessage

import top.fseasy.imlog.domain.model.AbsolutePathModel
import top.fseasy.imlog.domain.model.FileMetadataUnion
import top.fseasy.imlog.domain.model.MessageAudioProcessingErrorStage
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
    override val messageType: MessageType
        get() = MessageType.AUDIO

    override suspend fun getMetadataOrNull(
        storageRepository: StorageRepository,
        srcUriStr: UriStr,
    ): FileMetadataUnion? =
        storageRepository.getAudioMetadataOrNull(AbsolutePathModel.UriStrModel(srcUriStr))
            ?.toMetadataUnion()

    override val failureTypeMapper: ProcessingFailureTypeMapper
        get() = audioProcessingFailureTypeMapper

}

internal val audioProcessingFailureTypeMapper =
    ProcessingFailureTypeMapper(
        mapCacheCopyFailure = { copyFailureType ->
            when (copyFailureType) {
                CopyStageFailureType.CopyFile -> MessageAudioProcessingErrorStage.CopySrcToInternalCache
                CopyStageFailureType.SaveFilenameToDb -> MessageAudioProcessingErrorStage.SetInternalFilenameToDb
                CopyStageFailureType.UpdateDbIllegalState -> MessageAudioProcessingErrorStage.IllegalState
            }
        },
        mapSharedStorageCopyFailure = { copyFailureType ->
            when (copyFailureType) {
                CopyStageFailureType.CopyFile -> MessageAudioProcessingErrorStage.CopyToSharedStorage
                CopyStageFailureType.SaveFilenameToDb -> MessageAudioProcessingErrorStage.SetRawFilenameToDb
                CopyStageFailureType.UpdateDbIllegalState -> MessageAudioProcessingErrorStage.IllegalState
            }
        },
        mapThumbnailFailure = { error("Audio message don't have generate-thumbnail stage") },
        mapFinishTaskFailure = { finishFailureType ->
            when (finishFailureType) {
                FinishProcessingStageFailureType.DeleteCacheFile -> MessageAudioProcessingErrorStage.DeleteInternalFileCache
                FinishProcessingStageFailureType.DeleteTaskStateFromDb -> MessageAudioProcessingErrorStage.DeleteTaskStateFromDb
            }
        })