package top.fseasy.imlog.domain.usecase.sendfilemessage.stage

import kotlinx.coroutines.CancellationException
import timber.log.Timber
import top.fseasy.imlog.data.constants.TIMELINE_THUMBNAIL_COMPRESS_FORMAT
import top.fseasy.imlog.data.constants.TIMELINE_THUMBNAIL_COMPRESS_QUALITY
import top.fseasy.imlog.data.constants.TIMELINE_THUMBNAIL_MAX_SIZE
import top.fseasy.imlog.domain.model.AbsolutePathModel
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.domain.model.MessageType
import top.fseasy.imlog.domain.model.StoragePathModel
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UriStr
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.repository.MessageRepository
import top.fseasy.imlog.domain.repository.StorageRepository
import top.fseasy.imlog.domain.service.ImageThumbnailGenerateRequest
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
        srcUriStr: UriStr?,
        cacheFilePath: StoragePathModel.InternalOnly,
        messageType: MessageType,
    ): GenerateThumbnailStageResult {

        val generateFunction = when (messageType) {
            MessageType.AUDIO -> ::generateImageThumbnail
            else -> return GenerateThumbnailStageResult.Skip
        }
        val thumbnailBytes = try {
            generateOnTowSources(
                srcUriStr = srcUriStr,
                cacheFilePath = cacheFilePath,
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
    private suspend fun generateImageThumbnail(input: AbsolutePathModel): ByteArray {
        val scale = ThumbnailScale.FitMaxSize(maxSize = TIMELINE_THUMBNAIL_MAX_SIZE)
        val quality = TIMELINE_THUMBNAIL_COMPRESS_QUALITY
        val request = ImageThumbnailGenerateRequest(
            input = input,
            scale = scale,
            quality = quality,
            format = thumbnailFormat,
        )
        return thumbnailService.generateImageThumbnail(request)
    }

    /**
     * @throws Exception
     */
    private suspend fun generateVideoThumbnail(input: AbsolutePathModel): ByteArray {
        val scale = ThumbnailScale.FitMaxSize(maxSize = TIMELINE_THUMBNAIL_MAX_SIZE)
        val quality = TIMELINE_THUMBNAIL_COMPRESS_QUALITY
        val request = ImageThumbnailGenerateRequest(
            input = input,
            scale = scale,
            quality = quality,
            format = thumbnailFormat,
        )
        return thumbnailService.generateImageThumbnail(request)
    }

    /**
     * @throws Exception
     * @throws CancellationException
     */
    private suspend fun generateOnTowSources(
        srcUriStr: UriStr?,
        cacheFilePath: StoragePathModel.InternalOnly,
        executeGenerate: suspend (AbsolutePathModel) -> ByteArray,
    ): ByteArray {
        // First try source, continue if none or fail
        if (srcUriStr != null) {
            try {
                return executeGenerate(AbsolutePathModel.UriStrModel(srcUriStr))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Generate Thumbnail failed on source uri: $srcUriStr")
                // go to next
            }
        }
        // then try cache as input, throw when fail
        return executeGenerate(
            storageRepository.resolveStoragePathToAbsolutePathsWithoutCreating(cacheFilePath)
                .last()
        )
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

