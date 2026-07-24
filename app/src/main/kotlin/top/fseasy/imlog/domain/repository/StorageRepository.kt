package top.fseasy.imlog.domain.repository

import top.fseasy.imlog.domain.model.AbsolutePathModel
import top.fseasy.imlog.domain.model.AudioMetadata
import top.fseasy.imlog.domain.model.FileCopyResult
import top.fseasy.imlog.domain.model.FileDeleteResult
import top.fseasy.imlog.domain.model.GenericFileMetadata
import top.fseasy.imlog.domain.model.ImageMetadata
import top.fseasy.imlog.domain.model.StoragePathModel
import top.fseasy.imlog.domain.model.UriStr
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.model.VideoMetadata


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
     * Run in IO. No exception will be thrown. (all will be swallowed and return default)
     */
    suspend fun getDisplayNameOrDefault(uriStr: UriStr, defaultName: String): String

    /**
     * Transform @StoragePathModel to absolute path models (DuralWrite will get 2 models!)
     * without creating non-existed uri or files.
     * if this is a StoragePathModel.DuralWrite, will return a list ordering in `[UriStrModel, FileModel]`
     *
     * io parts run in IO.
     *
     * @throws Exception all exceptions came from @StoragePathModel.SharedStorageOnly.findUriOrThrow
     */
    suspend fun resolveStoragePathToAbsolutePathsWithoutCreating(storagePath: StoragePathModel): List<AbsolutePathModel>

    /** Run in IO thread in io parts.
     * @param mimeType: set it properly when filePathModel includes Uri.
     * @throws Exception
     * @return sharedStorageUri if filePathModel includes Uri, else null.
     */
    suspend fun writeFile(
        filePath: StoragePathModel,
        content: ByteArray,
        mimeType: String?,
    ): UriStr?

    /**
     * Run in IO threads for io parts.
     * @throws Exception
     * @return created dir UriStr if filePath contains Uri, else null.
     */
    suspend fun mkdirs(filePath: StoragePathModel): UriStr?

    /**
     * Run in IO.
     *
     * No exception thrown.
     *
     * TODO: optimize for condition input is a File. currently just transform it to Uri.
     *       it's suboptimal in efficiency and tolerance(permission is restricted in FileProvider)
     *
     * @param srcMimeType - it null, will resolve it internal for Uri type target.
     *                      For condition where targetPath is InternalOnly, leave it to null
     *                      as it's not needed
     */
    suspend fun copyFile(
        srcPath: StoragePathModel,
        targetPath: StoragePathModel,
        srcMimeType: String? = null,
    ): FileCopyResult

    /**
     * Run in IO.
     *
     * No exception thrown.
     *
     * TODO: optimize for condition input is a File. currently just transform it to Uri.
     *       it's suboptimal in efficiency and tolerance(permission is restricted in FileProvider)
     *
     * @param srcMimeType - it null, will resolve it internal for Uri type target.
     *                      For condition where targetPath is InternalOnly, leave it to null
     *                      as it's not needed
     */
    suspend fun copyFile(
        srcAbsolutePath: AbsolutePathModel,
        targetPath: StoragePathModel,
        srcMimeType: String? = null,
    ): FileCopyResult

    /** Run in IO thread in io parts.
     * Swallow all the exceptions.
     */
    suspend fun deleteFile(
        filePath: AbsolutePathModel,
    ): FileDeleteResult

    /** Run in IO thread in io parts.
     * Swallow all the exceptions.
     */
    suspend fun deleteFile(
        filePath: StoragePathModel,
    ): FileDeleteResult

    /**
     * No exception will be thrown. If path is invalid, return null. else return metadata
     * that might be inaccurate in edge condition where data is corrupted.
     *
     * Run in IO threads.
     */
    suspend fun getAudioMetadataOrNull(filePath: StoragePathModel): AudioMetadata?

    /**
     * No exception will be thrown. If uri invalid, return null. else return metadata
     * that might be inaccurate in edge condition where data is corrupted.
     *
     * Run in IO threads.
     */
    suspend fun getAudioMetadataOrNull(fileAbsolutePath: AbsolutePathModel): AudioMetadata?

    /**
     * No exception will be thrown. If uri invalid, return null. else return metadata
     * that might be inaccurate in edge condition where data is corrupted.
     *
     * Run in IO threads.
     */
    suspend fun getImageMetadataOrNull(fileAbsolutePath: AbsolutePathModel): ImageMetadata?

    /**
     * No exception will be thrown. If uri invalid, return null. else return metadata
     * that might be inaccurate in edge condition where data is corrupted.
     *
     * Run in IO threads.
     */
    suspend fun getVideoMetadataOrNull(fileAbsolutePath: AbsolutePathModel): VideoMetadata?

    /**
     * No exception will be thrown. If uri invalid, return null. else return metadata
     *
     * Run in IO threads.
     */
    suspend fun getGenericFileMetadataOrNull(fileAbsolutePath: AbsolutePathModel): GenericFileMetadata?

    /**
     * No exception will be thrown. If uri invalid, return null. else return mimetype
     *
     * Run in IO threads.
     */
    suspend fun getMimetypeOrNull(fileAbsolutePath: AbsolutePathModel): String?


}