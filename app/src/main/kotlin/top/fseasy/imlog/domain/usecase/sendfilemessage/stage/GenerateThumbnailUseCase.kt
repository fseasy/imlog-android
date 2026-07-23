package top.fseasy.imlog.domain.usecase.sendfilemessage.stage

import kotlinx.coroutines.CancellationException
import timber.log.Timber
import top.fseasy.imlog.data.constants.TIMELINE_THUMBNAIL_COMPRESS_FORMAT
import top.fseasy.imlog.data.constants.TIMELINE_THUMBNAIL_COMPRESS_QUALITY
import top.fseasy.imlog.data.constants.TIMELINE_THUMBNAIL_MAX_HEIGHT
import top.fseasy.imlog.data.constants.TIMELINE_THUMBNAIL_MAX_WIDTH
import top.fseasy.imlog.domain.model.AbsolutePathModel
import top.fseasy.imlog.domain.model.FileMetadataUnion
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.domain.model.MessageType
import top.fseasy.imlog.domain.model.StoragePathModel
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UriStr
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.repository.MessageRepository
import top.fseasy.imlog.domain.repository.StorageRepository
import top.fseasy.imlog.domain.service.ThumbnailGenerateRequest
import top.fseasy.imlog.domain.service.ThumbnailScale
import top.fseasy.imlog.domain.service.ThumbnailService
import top.fseasy.imlog.domain.usecase.StoragePathUseCase
import javax.inject.Inject

private val thumbnailFormat = TIMELINE_THUMBNAIL_COMPRESS_FORMAT

class GenerateThumbnailUseCase @Inject constructor(
    private val thumbnailService: ThumbnailService,
    private val storageRepository: StorageRepository,
    private val storagePathUseCase: StoragePathUseCase,
    private val messageRepository: MessageRepository,
) {

    /**
     * @param srcUriStr As Ui will first render with source uri, so first try this value
     *                  to share the thumbnail result if possible, which is inherent supported by Coil
     *
     * @throws CancellationException don't need to cache it!
     */
    suspend operator fun invoke(
        messageId: MessageId,
        userId: UserId,
        topicId: TopicId,
        messageTimestampMs: Long,
        messageType: MessageType,
        srcUriStr: UriStr?,
        cacheFilePath: StoragePathModel.InternalOnly,
        fileMetadata: FileMetadataUnion,
    ): GenerateThumbnailStageResult {

        val generateFunction = when (messageType) {
            MessageType.AUDIO -> ::generateImageThumbnail
            MessageType.VIDEO -> ::generateVideoThumbnail
            else -> return GenerateThumbnailStageResult.Skip
        }
        val thumbnailBytes = try {
            generateOnTowSources(
                srcUriStr = srcUriStr,
                cacheFilePath = cacheFilePath,
                fileMetadata = fileMetadata,
                executeGenerate = generateFunction
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to generate thumbnail on $messageId, type=$messageType")
            return GenerateThumbnailStageResult.Failure(
                GenerateThumbnailStageFailureType.Generate, true
            )
        }
        val thumbnailFilename = try {
            saveThumbnail(
                userId = userId, topicId = topicId,
                messageTimestampMs = messageTimestampMs,
                content = thumbnailBytes,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to save thumbnail on $messageId, $messageType")
            return GenerateThumbnailStageResult.Failure(
                GenerateThumbnailStageFailureType.SaveFile, true
            )
        }
        val isSetSuccess = try {
            messageRepository.setFileMessageThumbnailFilename(
                filename = thumbnailFilename, messageId = messageId
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to set thumbnail file name to db: $messageId, $messageType")
            return GenerateThumbnailStageResult.Failure(
                GenerateThumbnailStageFailureType.SetFilenameToDb, true
            )
        }
        return when (isSetSuccess) {
            false -> GenerateThumbnailStageResult.Failure(
                GenerateThumbnailStageFailureType.UpdateDbIllegalState, false
            )

            true -> GenerateThumbnailStageResult.Success
        }
    }

    /**
     * @throws Exception
     */
    private suspend fun generateImageThumbnail(
        input: AbsolutePathModel,
        fileMetadata: FileMetadataUnion,
    ): ByteArray {
        val request = buildThumbnailRequest(input, fileMetadata)
        return thumbnailService.generateImageThumbnail(request)
    }

    /**
     * @throws Exception
     */
    private suspend fun generateVideoThumbnail(
        input: AbsolutePathModel,
        fileMetadata: FileMetadataUnion,
    ): ByteArray {
        val request = buildThumbnailRequest(input, fileMetadata)
        return thumbnailService.generateVideoThumbnail(request)
    }

    /**
     * Currently the request is the same
     */
    private fun buildThumbnailRequest(
        input: AbsolutePathModel,
        fileMetadata: FileMetadataUnion,
    ): ThumbnailGenerateRequest {
        val inputWidth = requireNotNull(fileMetadata.width) { "width in metadata is null" }
        val inputHeight = requireNotNull(fileMetadata.height) { "height in metadata is null" }
        val scale = ThumbnailScale.ScaleToFit(
            TIMELINE_THUMBNAIL_MAX_WIDTH,
            TIMELINE_THUMBNAIL_MAX_HEIGHT
        )
        val quality = TIMELINE_THUMBNAIL_COMPRESS_QUALITY
        val request = ThumbnailGenerateRequest(
            input = input,
            inputWidth = inputWidth,
            inputHeight = inputHeight,
            scale = scale,
            quality = quality,
            format = thumbnailFormat,
        )
        return request
    }

    /**
     * @throws Exception
     * @throws CancellationException
     */
    private suspend fun generateOnTowSources(
        srcUriStr: UriStr?,
        cacheFilePath: StoragePathModel.InternalOnly,
        fileMetadata: FileMetadataUnion,
        executeGenerate: suspend (AbsolutePathModel, FileMetadataUnion) -> ByteArray,
    ): ByteArray {
        // First try source, continue if none or fail
        if (srcUriStr != null) {
            try {
                return executeGenerate(AbsolutePathModel.UriStrModel(srcUriStr), fileMetadata)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Generate Thumbnail failed on source uri: $srcUriStr")
                // go to next
            }
        }
        // then try cache as input, throw when fail
        val cacheAbsolutePath =
            storageRepository.resolveStoragePathToAbsolutePathsWithoutCreating(cacheFilePath)
                .last()
        return executeGenerate(cacheAbsolutePath, fileMetadata)
    }

    /**
     * @return filename
     * @throws Exception from writeFile
     */
    private suspend fun saveThumbnail(
        userId: UserId,
        topicId: TopicId,
        messageTimestampMs: Long,
        content: ByteArray,
    ): String {
        val filename = storagePathUseCase.buildUserFriendlyTimestampedFilename(
            timestampMs = messageTimestampMs,
            originalFilename = "thumbnail${thumbnailFormat.filenameSuffix}"
        )
        val path = storagePathUseCase.buildMessageThumbnailStoragePath(
            userId = userId,
            topicId = topicId,
            timestampMs = messageTimestampMs,
            filename = filename,
        )
        storageRepository.writeFile(path, content = content, mimeType = thumbnailFormat.mimeType)
        return filename
    }
}


enum class GenerateThumbnailStageFailureType {
    Generate, SaveFile, SetFilenameToDb, UpdateDbIllegalState
}

sealed interface GenerateThumbnailStageResult {
    data object Success : GenerateThumbnailStageResult
    data object Skip : GenerateThumbnailStageResult
    data class Failure(val type: GenerateThumbnailStageFailureType, val retryable: Boolean) :
        GenerateThumbnailStageResult
}

