package top.fseasy.imlog.domain.repository

import top.fseasy.imlog.domain.model.FilePathModel
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UriStr
import top.fseasy.imlog.domain.model.UserId
import java.io.File


data class SavedMedia(
    val filename: String,
    val originalFilename: String,
    val fileSize: Long,
    val thumbnailFilename: String?,
    val mimeType: String,
    val duration: Long?, // for video, audio
    val width: Int?,
    val height: Int?,
)

sealed interface MediaSaveResult {
    /**
     * Full/relative path/uri will be dynamically calculated
     */
    data class Success(val savedMedia: SavedMedia) : MediaSaveResult

    sealed interface Failure : MediaSaveResult {
        val cause: Throwable

        data class TgtInvalid(override val cause: Throwable) : Failure
        data class SrcInvalid(override val cause: Throwable) : Failure
        data class Unexpected(override val cause: Throwable) : Failure
    }
}

/**
 * StorageRepository: 1. thumbnail root dir 2. manage media-storage-root-uri 3. save message media file
 * NOTE: in the api, we use `String` instead of Uri to follow the clean rule.
 */
interface StorageRepository {

    /** StorageRepo will hold the shared storage root uri for each user.
     * save uri to user preference and update flag field in app-init-data table.
     * @param uriStr if null, will reset the init-data.
     */
    suspend fun setSharedStorageRootUriAndUpdateInitData(
        userId: UserId,
        uriStr: UriStr?,
    ): Result<Unit>

    /**
     * Perform media (img, video) saving logics:
     * 1. copy raw to shared storage
     * 2. generate thumbnail, save to private external storage
     */
    suspend fun saveMessageMedia(
        userId: UserId,
        topicId: TopicId,
        srcUriStr: UriStr,
        messageTimestampMs: Long,
    ): MediaSaveResult

    suspend fun getDisplayNameOrThrow(uriStr: UriStr): String

    suspend fun writeFile(filePath: FilePathModel, content: ByteArray, mimeType: String?): UriStr?
    suspend fun mkdirs(filePath: FilePathModel): UriStr?
}