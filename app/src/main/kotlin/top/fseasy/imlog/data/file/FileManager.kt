package top.fseasy.imlog.data.file

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import top.fseasy.imlog.data.file.MediaSaveResult.*
import top.fseasy.imlog.domain.model.MessageType
import top.fseasy.imlog.domain.model.UserId
import javax.inject.Inject

sealed interface MediaSaveResult {
    /**
     * Full/relative path/uri will be dynamically calculated
     */
    data class SavedMedia(
        val filename: String,
        val originalFilename: String,
        val fileSize: Long,
        val thumbnailFilename: String,
        val mimeType: String,
        val duration: Long?, // for video, audio
        val width: Int,
        val height: Int,
    ) : MediaSaveResult

    data class MediaSavePermissionError(val cause: Throwable) : MediaSaveResult
    data class MediaSaveSrcInvalidError(val cause: Throwable) : MediaSaveResult
    data class MediaSaveUnexpectedError(val cause: Throwable) : MediaSaveResult
}

class FileManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Perform media (img, video) saving logics:
     * 1. copy raw to shared storage
     * 2. generate thumbnail, save to private external storage
     */
    suspend fun saveMessageMedia(
        userId: UserId,
        topicId: UserId,
        srcUri: Uri,
        messageTimestampMs: Long,
        messageType: MessageType,
        tgtRootTreeUri: Uri,
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
            MessageFilePath.generateFilenameByPrependTime(messageTimestampMs, displayName)
        val mimeType =
            metaData.mimeType ?: UriStorageUtil.getMimeTypeFallback(context, srcUri)
        val relativePathSegments =
            MessageFilePath.fullRelativePath(userId, topicId, messageTimestampMs, storeRawFilename)
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

        // generate thumbnail


        val fileSize = metaData.fileSize ?: 0L

    }
}