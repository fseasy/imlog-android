package top.fseasy.imlog.data.file

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import timber.log.Timber
import top.fseasy.imlog.constants.THUMBNAIL_MAX_HEIGHT
import top.fseasy.imlog.constants.THUMBNAIL_MAX_WIDTH
import top.fseasy.imlog.data.file.MediaSaveResult.*
import top.fseasy.imlog.domain.model.MessageType
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.util.FileCopyResult
import top.fseasy.imlog.util.FileWriteMode
import top.fseasy.imlog.util.FindFileResult
import top.fseasy.imlog.util.GenerateThumbnailResult
import top.fseasy.imlog.util.MediaFields
import top.fseasy.imlog.util.UriStorageUtil
import top.fseasy.imlog.util.generateAndSaveThumbnail
import top.fseasy.imlog.util.isDimensionValid
import top.fseasy.imlog.util.resolveSubPaths
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
        val tgtRootTreeUri = fileRootDir.messageFileRootUri.firstOrNull()
            ?: return MediaSavePermissionError(
                IllegalStateException("message storage dir isn't accessible, pick again?")
            )

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
        val relativePathSegments =
            MessageFilePathRule.generateFullRelativePath(
                userId,
                topicId,
                messageTimestampMs,
                storeRawFilename
            )
        val tgtUriFindResult =
            UriStorageUtil.ensureSAFFileUri(context, tgtRootTreeUri, relativePathSegments, mimeType)
        val rawFileTgtUri = when (tgtUriFindResult) {
            is FindFileResult.Success -> tgtUriFindResult.uri
            is FindFileResult.Error.PermissionDenied -> return MediaSavePermissionError(
                tgtUriFindResult.cause
            )

            is FindFileResult.Error.Unexpected -> return MediaSaveUnexpectedError(
                tgtUriFindResult.cause
            )

            is FindFileResult.NotFound -> return MediaSaveUnexpectedError(IllegalStateException("EnsureSAFFile got Not Found"))
        }

        val copyResult = UriStorageUtil.copyBetweenUri(
            context,
            srcUri,
            rawFileTgtUri,
            writeMode = FileWriteMode.WRITE_TRUNCATE,
        )

        when (copyResult) {
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

            is FileCopyResult.Success -> {}
        }
        // Following logics will be different on different mime
        val isImage = mimeType.startsWith("image")
        val isVideo = mimeType.startsWith("video")
        val isAudio = mimeType.startsWith("audio")

        var thumbnailFilename: String? = null
        if (isImage || isVideo) {
            // generate thumbnail
            // First try to use the srcUri to share the cache (gen in the UI show up)
            thumbnailFilename =
                MessageFilePathRule.generateFilenameByPrependTime(messageTimestampMs, "thumb.webp")
            val thumbnailFullRelativePath =
                MessageFilePathRule.generateFullRelativePath(
                    userId = userId,
                    topicId = topicId,
                    timestampMs = messageTimestampMs,
                    filename = thumbnailFilename
                )
            val thumbnailFile =
                fileRootDir.thumbnailRootDir.resolveSubPaths(
                    thumbnailFullRelativePath,
                    true,
                    lastPathIsFile = true
                )
            val uriCandidates = listOf("src" to srcUri, "tgt" to rawFileTgtUri)
            var generateThumbnailOk = false
            var generateThumbnailLastError: Throwable? = null
            for ((uriName, tryUri) in uriCandidates) {
                val generateThumbResult = generateAndSaveThumbnail(
                    context, tryUri,
                    targetFile = thumbnailFile,
                    maxWidth = THUMBNAIL_MAX_WIDTH,
                    maxHeight = THUMBNAIL_MAX_HEIGHT
                )
                when (generateThumbResult) {
                    is GenerateThumbnailResult.Success -> {
                        generateThumbnailOk = true
                        break
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
            if (!generateThumbnailOk) {
                return MediaSaveUnexpectedError(generateThumbnailLastError!!)
            }
        }

        var width = metaData.width
        var height = metaData.height
        var duration = metaData.duration
        if (isImage && !isDimensionValid(width, height)) {
            UriStorageUtil.getImageDimensionsFallback(context, srcUri)
                ?.also {
                    width = it.width
                    height = it.height
                }
        } else if (isVideo && !(isDimensionValid(
                width,
                height
            ) && duration != null && duration > 0L)
        ) {
            UriStorageUtil.getVideo3DimensionsFallback(context, srcUri)
                ?.also {
                    width = it.width
                    height = it.height
                    duration = it.duration
                }
        } else if (isAudio && !(duration != null && duration > 0L)) {
            duration = UriStorageUtil.getAudioDurationFallback(context, srcUri)
        }

        val fileSize = metaData.fileSize ?: copyResult.bytesCopied
        return SavedMedia(
            filename = storeRawFilename,
            originalFilename = displayName,
            fileSize = fileSize,
            thumbnailFilename = thumbnailFilename,
            mimeType = mimeType,
            duration = duration,
            width = width,
            height = height
        )
    }
}