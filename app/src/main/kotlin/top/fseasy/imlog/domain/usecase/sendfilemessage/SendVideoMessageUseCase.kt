package top.fseasy.imlog.domain.usecase.sendfilemessage

import top.fseasy.imlog.domain.model.AbsolutePathModel
import top.fseasy.imlog.domain.model.FileMetadataUnion
import top.fseasy.imlog.domain.model.MessageVideoProcessingErrorStage
import top.fseasy.imlog.domain.model.MessageType
import top.fseasy.imlog.domain.model.UriStr
import top.fseasy.imlog.domain.model.toMetadataUnion
import top.fseasy.imlog.domain.repository.StorageRepository
import top.fseasy.imlog.domain.usecase.sendfilemessage.stage.CopyStageFailureType
import top.fseasy.imlog.domain.usecase.sendfilemessage.stage.FinishProcessingStageFailureType
import top.fseasy.imlog.domain.usecase.sendfilemessage.stage.GenerateThumbnailStageFailureType
import javax.inject.Inject

class SendVideoMessageUseCase @Inject constructor(
    dependencies: SendUriUseCaseBaseDependencies,
) : SendUriUseCaseBase(dependencies) {
    override val messageType: MessageType
        get() = MessageType.VIDEO

    override suspend fun getMetadataOrNull(
        storageRepository: StorageRepository,
        srcUriStr: UriStr,
    ): FileMetadataUnion? =
        storageRepository.getVideoMetadataOrNull(AbsolutePathModel.UriStrModel(srcUriStr))
            ?.toMetadataUnion()

    override val failureTypeMapper: ProcessingFailureTypeMapper
        get() = videoProcessingFailureTypeMapper
}

internal val videoProcessingFailureTypeMapper = ProcessingFailureTypeMapper(
    mapCacheCopyFailure = { copyFailureType ->
        when (copyFailureType) {
            CopyStageFailureType.CopyFile -> MessageVideoProcessingErrorStage.CopySrcToInternalCache
            CopyStageFailureType.SaveFilenameToDb -> MessageVideoProcessingErrorStage.SetInternalFilenameToDb
            CopyStageFailureType.UpdateDbIllegalState -> MessageVideoProcessingErrorStage.IllegalState
        }
    },
    mapSharedStorageCopyFailure = { copyFailureType ->
        when (copyFailureType) {
            CopyStageFailureType.CopyFile -> MessageVideoProcessingErrorStage.CopyToSharedStorage
            CopyStageFailureType.SaveFilenameToDb -> MessageVideoProcessingErrorStage.SetRawFilenameToDb
            CopyStageFailureType.UpdateDbIllegalState -> MessageVideoProcessingErrorStage.IllegalState
        }
    },
    mapThumbnailFailure = { thumbnailFailureType ->
        when (thumbnailFailureType) {
            GenerateThumbnailStageFailureType.Generate -> MessageVideoProcessingErrorStage.GenerateThumbnail
            GenerateThumbnailStageFailureType.SaveFile -> MessageVideoProcessingErrorStage.SaveThumbnailFile
            GenerateThumbnailStageFailureType.SetFilenameToDb -> MessageVideoProcessingErrorStage.SetThumbnailFilenameToDb
            GenerateThumbnailStageFailureType.UpdateDbIllegalState -> MessageVideoProcessingErrorStage.IllegalState
        }
    },
    mapFinishTaskFailure = { finishFailureType ->
        when (finishFailureType) {
            FinishProcessingStageFailureType.DeleteCacheFile -> MessageVideoProcessingErrorStage.DeleteInternalFileCache
            FinishProcessingStageFailureType.DeleteTaskStateFromDb -> MessageVideoProcessingErrorStage.DeleteTaskStateFromDb
        }
    }
)