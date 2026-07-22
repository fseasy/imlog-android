package top.fseasy.imlog.domain.usecase.sendfilemessage

import top.fseasy.imlog.domain.model.FileMetadataUnion
import top.fseasy.imlog.domain.model.MessageType
import top.fseasy.imlog.domain.model.MessageVoiceProcessingErrorStage
import top.fseasy.imlog.domain.model.StoragePathModel
import top.fseasy.imlog.domain.model.toMetadataUnion
import top.fseasy.imlog.domain.repository.StorageRepository
import top.fseasy.imlog.domain.usecase.sendfilemessage.stage.CopyStageFailureType
import top.fseasy.imlog.domain.usecase.sendfilemessage.stage.FinishProcessingStageFailureType
import javax.inject.Inject

class SendVoiceMessageUseCase @Inject constructor(dependencies: SendCacheFileUseCaseBaseDependencies) :
    SendCacheFileUseCaseBase(dependencies) {
    override val messageType: MessageType
        get() = MessageType.VOICE

    override suspend fun getMetadataOrNull(
        storageRepository: StorageRepository,
        cacheFile: StoragePathModel.InternalOnly,
    ): FileMetadataUnion? = storageRepository.getAudioMetadataOrNull(cacheFile)
        ?.toMetadataUnion()

    override val failureTypeMapper: ProcessingFailureTypeMapper
        get() = voiceProcessingFailureTypeMapper
}

internal val voiceProcessingFailureTypeMapper =
    ProcessingFailureTypeMapper(
        mapCacheCopyFailure = { error("No cache copy stage in voice processing") },
        mapSharedStorageCopyFailure = { copyFailureType ->
            when (copyFailureType) {
                CopyStageFailureType.CopyFile -> MessageVoiceProcessingErrorStage.CopyToSharedStorage
                CopyStageFailureType.SaveFilenameToDb -> MessageVoiceProcessingErrorStage.SetRawFilenameToDb
                CopyStageFailureType.UpdateDbIllegalState -> MessageVoiceProcessingErrorStage.IllegalState
            }
        },
        mapThumbnailFailure = { error("No generate-thumbnail stage in voice processing") },
        mapFinishTaskFailure = { finishFailureType ->
            when (finishFailureType) {
                FinishProcessingStageFailureType.DeleteCacheFile -> MessageVoiceProcessingErrorStage.DeleteInternalFileCache
                FinishProcessingStageFailureType.DeleteTaskStateFromDb -> MessageVoiceProcessingErrorStage.DeleteTaskStateFromDb
            }
        })