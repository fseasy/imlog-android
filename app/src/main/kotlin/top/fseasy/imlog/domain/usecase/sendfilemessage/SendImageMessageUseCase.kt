package top.fseasy.imlog.domain.usecase.sendfilemessage

import top.fseasy.imlog.domain.model.AbsolutePathModel
import top.fseasy.imlog.domain.model.FileMetadataUnion
import top.fseasy.imlog.domain.model.MessageImageProcessingErrorStage
import top.fseasy.imlog.domain.model.MessageType
import top.fseasy.imlog.domain.model.UriStr
import top.fseasy.imlog.domain.model.toMetadataUnion
import top.fseasy.imlog.domain.repository.StorageRepository
import top.fseasy.imlog.domain.usecase.sendfilemessage.stage.CopyStageFailureType
import top.fseasy.imlog.domain.usecase.sendfilemessage.stage.FinishProcessingStageFailureType
import top.fseasy.imlog.domain.usecase.sendfilemessage.stage.GenerateThumbnailStageFailureType
import javax.inject.Inject

class SendImageMessageUseCase @Inject constructor(
    dependencies: SendUriUseCaseBaseDependencies,
) : SendUriUseCaseBase(dependencies) {
    override val messageType: MessageType
        get() = MessageType.IMAGE

    override suspend fun getMetadataOrNull(
        storageRepository: StorageRepository,
        srcUriStr: UriStr,
    ): FileMetadataUnion? =
        storageRepository.getImageMetadataOrNull(AbsolutePathModel.UriStrModel(srcUriStr))
            ?.toMetadataUnion()

    override val failureTypeMapper: ProcessingFailureTypeMapper
        get() = imageProcessingFailureTypeMapper
}

internal val imageProcessingFailureTypeMapper = ProcessingFailureTypeMapper(
    mapCacheCopyFailure = { copyFailureType ->
        when (copyFailureType) {
            CopyStageFailureType.CopyFile -> MessageImageProcessingErrorStage.CopySrcToInternalCache
            CopyStageFailureType.SaveFilenameToDb -> MessageImageProcessingErrorStage.SetInternalFilenameToDb
            CopyStageFailureType.UpdateDbIllegalState -> MessageImageProcessingErrorStage.IllegalState
        }
    },
    mapSharedStorageCopyFailure = { copyFailureType ->
        when (copyFailureType) {
            CopyStageFailureType.CopyFile -> MessageImageProcessingErrorStage.CopyToSharedStorage
            CopyStageFailureType.SaveFilenameToDb -> MessageImageProcessingErrorStage.SetRawFilenameToDb
            CopyStageFailureType.UpdateDbIllegalState -> MessageImageProcessingErrorStage.IllegalState
        }
    },
    mapThumbnailFailure = { thumbnailFailureType ->
        when (thumbnailFailureType) {
            GenerateThumbnailStageFailureType.Generate -> MessageImageProcessingErrorStage.GenerateThumbnail
            GenerateThumbnailStageFailureType.SaveFile -> MessageImageProcessingErrorStage.SaveThumbnailFile
            GenerateThumbnailStageFailureType.SetFilenameToDb -> MessageImageProcessingErrorStage.SetThumbnailFilenameToDb
            GenerateThumbnailStageFailureType.UpdateDbIllegalState -> MessageImageProcessingErrorStage.IllegalState
        }
    },
    mapFinishTaskFailure = { finishFailureType ->
        when (finishFailureType) {
            FinishProcessingStageFailureType.DeleteCacheFile -> MessageImageProcessingErrorStage.DeleteInternalFileCache
            FinishProcessingStageFailureType.DeleteTaskStateFromDb -> MessageImageProcessingErrorStage.DeleteTaskStateFromDb
        }
    }
)