package top.fseasy.imlog.data.file

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import timber.log.Timber
import top.fseasy.imlog.constants.THUMBNAIL_MAX_HEIGHT
import top.fseasy.imlog.constants.THUMBNAIL_MAX_WIDTH
import top.fseasy.imlog.data.file.MediaSaveResult.*
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.util.ContentResolverQueriedResult
import top.fseasy.imlog.util.FileCopyResult
import top.fseasy.imlog.util.FileWriteMode
import top.fseasy.imlog.util.FindOrCreateFileUriResult
import top.fseasy.imlog.util.GenerateThumbnailResult
import top.fseasy.imlog.util.MediaFields
import top.fseasy.imlog.util.UriStorageUtil
import top.fseasy.imlog.util.generateAndSaveThumbnail
import top.fseasy.imlog.util.isDimensionValid
import top.fseasy.imlog.util.resolveSubPaths
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed interface MediaSaveResult {
    /**
     * Full/relative path/uri will be dynamically calculated
     */
    data class SavedMedia(
        val filename: String,
        val originalFilename: String,
        val fileSize: Long,
        val thumbnailFilename: String?,
        val mimeType: String,
        val duration: Long?, // for video, audio
        val width: Int?,
        val height: Int?,
    ) : MediaSaveResult

    data class MediaSavePermissionError(val cause: Throwable) : MediaSaveResult
    data class MediaSaveSrcInvalidError(val cause: Throwable) : MediaSaveResult
    data class MediaSaveUnexpectedError(val cause: Throwable) : MediaSaveResult
}

@Singleton
class FileManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val fileRootDir: FileRootDir,
) {
    /**
     * Perform media (img, video) saving logics:
     * 1. copy raw to shared storage
     * 2. generate thumbnail, save to private external storage
     */
    suspend fun saveMessageMedia(
        userId: UserId,
        topicId: TopicId,
        srcUri: Uri,
        messageTimestampMs: Long,
    ): MediaSaveResult {
        val queryMetaFields = with(MediaFields) {
            listOf(
                DISPLAY_NAME, FILE_SIZE, MIME_TYPE, DURATION, HEIGHT, WIDTH
            )
        }

        val metaData = UriStorageUtil.resolveMetadata(context, srcUri, queryMetaFields)

        val displayName = metaData.displayName
            ?: UriStorageUtil.getFilenameFallback(srcUri) // more robust
            ?: "_" // have to assign a value. It should be ok.

        val storeRawFilename =
            MessageFilePathRule.generateFilenameByPrependTime(messageTimestampMs, displayName)
        val mimeType =
            metaData.mimeType ?: UriStorageUtil.getMimeTypeFallback(context, srcUri)
        val rawFileTgtUri = when (val result = calcRawSharedStorageUri(
            userId = userId,
            topicId = topicId,
            messageTimestampMs = messageTimestampMs,
            rawFilename = storeRawFilename,
            mimeType = mimeType
        )) {
            is FindOrCreateFileUriResult.Success -> result.uri
            // Will return on errors
            is FindOrCreateFileUriResult.Error.PermissionDenied -> return MediaSavePermissionError(
                result.cause
            )

            is FindOrCreateFileUriResult.Error.Unexpected -> return MediaSaveUnexpectedError(
                result.cause
            )

            is FindOrCreateFileUriResult.NotFound -> return MediaSaveUnexpectedError(
                IllegalStateException("EnsureSAFFile got Not Found")
            )
        }

        val bytesCopied = when (val copyResult = UriStorageUtil.copyBetweenUri(
            context,
            srcUri,
            rawFileTgtUri,
            writeMode = FileWriteMode.WRITE_TRUNCATE,
        )) {
            is FileCopyResult.Success -> {
                copyResult.bytesCopied
            }
            // Else return error
            is FileCopyResult.Error.SrcPermissionDenied,
            is FileCopyResult.Error.SrcNotFound,
            is FileCopyResult.Error.SrcOpenUnexpected,
                -> return MediaSaveSrcInvalidError(
                copyResult.cause
            )

            is FileCopyResult.Error.TgtPermissionDenied -> return MediaSavePermissionError(
                copyResult.cause
            )

            is FileCopyResult.Error.TgtNotFound,
            is FileCopyResult.Error.TgtOpenUnexpected,
            is FileCopyResult.Error.CopyIOError,
            is FileCopyResult.Error.CopyUnexpected,
                -> return MediaSaveUnexpectedError(
                IllegalStateException(copyResult.cause)
            )
        }
        // Following logics will be different on different mime
        val isImage = mimeType.startsWith("image")
        val isVideo = mimeType.startsWith("video")
        val isAudio = mimeType.startsWith("audio")

        // generate thumbnail
        val thumbnailFilename = if (isImage || isVideo) {
            val name =
                MessageFilePathRule.generateFilenameByPrependTime(messageTimestampMs, "thumb.webp")
            val uriCandidates = listOf("src" to srcUri, "tgt" to rawFileTgtUri)
            val (isOK, lastError) = generateImageOrVideoThumbnailFromSrcCandidates(
                userId = userId,
                topicId = topicId,
                messageTimestampMs = messageTimestampMs,
                thumbnailFilename = name,
                srcUriCandidates = uriCandidates
            )
            if (!isOK) {
                return MediaSaveUnexpectedError(lastError!!) // Exit quickly when failed
            }
            name
        } else null
        // First try to use the srcUri to share the cache (gen in the UI show up)
        val finalDimensionAndDuration = ensureValidDimensionAndDuration(
            isImage = isImage,
            isVideo = isVideo,
            isAudio = isAudio,
            mediaUri = rawFileTgtUri,
            initialMeta = metaData
        )

        val fileSize = metaData.fileSize ?: bytesCopied
        return SavedMedia(
            filename = storeRawFilename,
            originalFilename = displayName,
            fileSize = fileSize,
            thumbnailFilename = thumbnailFilename,
            mimeType = mimeType,
            duration = finalDimensionAndDuration.duration,
            width = finalDimensionAndDuration.width,
            height = finalDimensionAndDuration.height
        )
    }

    suspend fun generateImageOrVideoThumbnailFromSrcCandidates(
        userId: UserId,
        topicId: TopicId,
        messageTimestampMs: Long,
        thumbnailFilename: String,
        srcUriCandidates: List<Pair<String, Uri>>,
    ): Pair<Boolean, Throwable?> {
        val thumbnailFile = calcThumbnailFile(
            userId = userId,
            topicId = topicId,
            messageTimestampMs = messageTimestampMs,
            thumbnailFilename = thumbnailFilename
        )

        var generateThumbnailLastError: Throwable? = null
        for ((uriName, tryUri) in srcUriCandidates) {

            when (val generateThumbResult = generateAndSaveThumbnail(
                context, tryUri,
                targetFile = thumbnailFile,
                maxWidth = THUMBNAIL_MAX_WIDTH,
                maxHeight = THUMBNAIL_MAX_HEIGHT
            )) {
                is GenerateThumbnailResult.Success -> {
                    return true to null
                }

                is GenerateThumbnailResult.Error -> {
                    Timber.i(
                        generateThumbResult.cause,
                        "Generate thumbnail failed for $uriName Uri "
                    )
                    generateThumbnailLastError = generateThumbResult.cause
                }
            }
        }
        return false to generateThumbnailLastError
    }

    fun calcThumbnailFile(
        userId: UserId,
        topicId: TopicId,
        messageTimestampMs: Long,
        thumbnailFilename: String,
    ): File {
        val thumbnailFullRelativePath =
            MessageFilePathRule.generateFullRelativePath(
                userId = userId,
                topicId = topicId,
                timestampMs = messageTimestampMs,
                filename = thumbnailFilename
            )
        return fileRootDir.thumbnailRootDir.resolveSubPaths(
            thumbnailFullRelativePath,
            true,
            lastPathIsFile = true
        )
    }

    suspend fun calcRawSharedStorageUri(
        userId: UserId,
        topicId: TopicId,
        messageTimestampMs: Long,
        rawFilename: String,
        mimeType: String,
    ): FindOrCreateFileUriResult {
        val tgtRootTreeUri = fileRootDir.messageFileRootUri.firstOrNull()
            ?: return FindOrCreateFileUriResult.Error.Unexpected(
                IllegalStateException("messageFileRootUri is Null")
            )
        val relativePathSegments =
            MessageFilePathRule.generateFullRelativePath(
                userId,
                topicId,
                messageTimestampMs,
                rawFilename
            )
        return UriStorageUtil.ensureSAFFileUri(
            context,
            tgtRootTreeUri,
            relativePathSegments,
            mimeType
        )
    }


    private data class DimensionAndDuration(
        val width: Int?,
        val height: Int?,
        val duration: Long?,
    )

    private fun ensureValidDimensionAndDuration(
        isImage: Boolean,
        isVideo: Boolean,
        isAudio: Boolean,
        mediaUri: Uri,
        initialMeta: ContentResolverQueriedResult,
    ): DimensionAndDuration {
        val isDurationValid = (initialMeta.duration ?: 0L) > 0L

        // 使用 when(true) 进行多条件分支判断
        return when {
            isImage && !isDimensionValid(initialMeta.width, initialMeta.height) -> {
                UriStorageUtil.getImageDimensionsFallback(context, mediaUri)
                    ?.let {
                        DimensionAndDuration(it.width, it.height, 0L)
                    } ?: DimensionAndDuration(
                    initialMeta.width,
                    initialMeta.height,
                    initialMeta.duration
                )
            }

            isVideo && !(isDimensionValid(
                initialMeta.width,
                initialMeta.height
            ) && isDurationValid) -> {
                UriStorageUtil.getVideo3DimensionsFallback(context, mediaUri)
                    ?.let {
                        DimensionAndDuration(it.width, it.height, it.duration)
                    } ?: DimensionAndDuration(
                    initialMeta.width,
                    initialMeta.height,
                    initialMeta.duration
                )
            }

            isAudio && !isDurationValid -> {
                val d = UriStorageUtil.getAudioDurationFallback(context, mediaUri)
                    ?: initialMeta.duration
                DimensionAndDuration(null, null, d)
            }

            else -> DimensionAndDuration(
                initialMeta.width,
                initialMeta.height,
                initialMeta.duration
            )
        }
    }
}