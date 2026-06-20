package top.fseasy.imlog.data.repository

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import top.fseasy.imlog.data.constants.THUMBNAIL_MAX_HEIGHT
import top.fseasy.imlog.data.constants.THUMBNAIL_MAX_WIDTH
import top.fseasy.imlog.data.mapper.toUriOrThrow
import top.fseasy.imlog.data.mapper.toUriStr
import top.fseasy.imlog.domain.model.FilePathModel
import top.fseasy.imlog.domain.model.SharedStorageRootSource
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UriStr
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.repository.MediaSaveResult
import top.fseasy.imlog.domain.repository.SavedMedia
import top.fseasy.imlog.domain.repository.StorageRepository
import top.fseasy.imlog.sqldelight.SqlDelightDb
import top.fseasy.imlog.data.util.ContentResolverQueriedResult
import top.fseasy.imlog.data.util.FileCopyResult
import top.fseasy.imlog.data.util.FileWriteMode
import top.fseasy.imlog.data.util.FindOrCreateFileUriResult
import top.fseasy.imlog.data.util.GenerateThumbnailResult
import top.fseasy.imlog.data.util.MediaFields
import top.fseasy.imlog.data.util.UriStorageUtil
import top.fseasy.imlog.data.util.WriteDataResult
import top.fseasy.imlog.data.util.generateAndSaveThumbnail
import top.fseasy.imlog.data.util.isDimensionValid
import top.fseasy.imlog.domain.util.resolveSubPaths
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class StorageRepositoryImpl @Inject constructor(
    private val database: SqlDelightDb,
    @param:ApplicationContext private val context: Context,
    private val dispatcher: CoroutineDispatcher,
) : StorageRepository {

    private val storageUriCache = mutableMapOf<UserId, Uri?>()

    /**
     * 1. try to get from cache 2. else get from DB (run in IO thread)
     */
    private suspend fun getSharedStorageRootUriWithCache(userId: UserId): Uri? {
        if (storageUriCache.containsKey(userId)) {
            return storageUriCache[userId]
        }
        // lookup db
        val uri = withContext(dispatcher) {
            database.userPreferenceQueries.getSharedStorageRootUri(userId.value)
                .executeAsOneOrNull()?.shared_storage_root_uri?.toUri()
        }
        storageUriCache[userId] = uri
        return uri
    }

    override suspend fun setSharedStorageRootUriAndUpdateInitData(
        userId: UserId,
        uriStr: UriStr?,
    ): Result<Unit> = runCatching {
        val uri = uriStr?.toUriOrThrow() // Parse first to verify
        withContext(dispatcher) {
            database.transaction {
                database.userPreferenceQueries.upsertSharedStorageRootUri(
                    userId = userId.value, storageRootUri = uriStr?.value
                )
                database.appInitDataQueries.updateStorageUriSelected(
                    isSelected = if (uriStr != null) 1L else 0L, userId = userId.value
                )
            }
        }
        storageUriCache[userId] = uri
    }

    override suspend fun getDisplayNameOrThrow(uriStr: UriStr): String {
        val uri = uriStr.toUriOrThrow()
        return UriStorageUtil.getDisplayNameWithFallback(context, uri = uri)
            ?: throw IllegalArgumentException(
                "Given Uri $uriStr can't be parsed to get dir name"
            )
    }

    override suspend fun mkdirsBasedOnUriRoot(
        subDirs: List<String>,
        rootUriStr: UriStr,
    ): UriStr = mkdirsBasedOnUriRoot(subDirs, rootUriStr.toUriOrThrow()).toUriStr()


    override suspend fun mkdirsBasedOnUserSharedStorageRoot(
        userId: UserId,
        subDirs: List<String>,
    ): UriStr {
        val rootUri = getSharedStorageRootUriWithCache(userId)
            ?: throw IllegalStateException("Storage root URI for current user $userId is null.")

        return mkdirsBasedOnUriRoot(
            subDirs, rootUri
        ).toUriStr()
    }

    private suspend fun mkdirsBasedOnUriRoot(
        subDirs: List<String>,
        rootUri: Uri,
    ): Uri {
        val result = UriStorageUtil.ensureSAFDirectoryUri(
            context = context, rootTreeUri = rootUri, relativePathSegments = subDirs
        )
        return when (result) {
            is FindOrCreateFileUriResult.Success -> result.uri

            is FindOrCreateFileUriResult.NotFound -> throw IllegalStateException("Get NotFound while calling ensureUri")
            is FindOrCreateFileUriResult.Error -> throw result.cause
        }
    }

    /**
     * @param mimeType: set it properly when filePathModel includes Uri.
     */
    override suspend fun writeFile(
        filePath: FilePathModel,
        content: ByteArray,
        mimeType: String?,
    ): UriStr {
        when (filePath) {
            is FilePathModel.DualWrite -> {
                val effectiveMimeType =
                    requireNotNull(mimeType) { "Must Set mimeType for DualWrite path" }
                writeExternal()
                writeFileForSharedStorageOnly(
                    filePath.toSharedStorageOnly(),
                    content,
                    effectiveMimeType
                )
            }

            is FilePathModel.SharedStorageOnly -> {
                val effectiveMimeType =
                    requireNotNull(mimeType) { "Must Set mimeType for SharedStorageOnly path" }
                writeFileForSharedStorageOnly(filePath, content, effectiveMimeType)
            }

            is FilePathModel.ExternalOnly -> {
                writeExternal()
            }
        }
    }

    /** Run in IO in necessary parts.
     * @throws Exception
     */
    private suspend fun writeFileForSharedStorageOnly(
        filePath: FilePathModel.SharedStorageOnly,
        content: ByteArray,
        mimeType: String,
    ): UriStr {
        val rootUri = when (val root = filePath.root) {
            is SharedStorageRootSource.Direct -> root.uriStr.toUriOrThrow()
            is SharedStorageRootSource.LookupByUser -> getSharedStorageRootUriWithCache(root.userId)
                ?: throw IllegalStateException("Storage root URI for current user ${root.userId} is null.")
        }

        val fileUri = when (val result = UriStorageUtil.ensureSAFFileUri(
            context = context,
            rootTreeUri = rootUri,
            relativePathSegments = filePath.fullRelativePath,
            fileMimeType = mimeType,
        )) {
            is FindOrCreateFileUriResult.Success -> result.uri
            is FindOrCreateFileUriResult.NotFound -> throw IllegalStateException("Get NotFound while calling ensureUri")
            is FindOrCreateFileUriResult.Error -> throw result.cause
        }
        when (val r = UriStorageUtil.writeData(context, tgtFileUri = fileUri, content = content)) {
            is WriteDataResult.Error -> throw r.cause
            is WriteDataResult.Success -> Unit
        }
        return fileUri.toUriStr()
    }


    override suspend fun saveMessageMedia(
        userId: UserId,
        topicId: TopicId,
        srcUriStr: UriStr,
        messageTimestampMs: Long,
    ): MediaSaveResult {
        val queryMetaFields = with(MediaFields) {
            listOf(
                DISPLAY_NAME, FILE_SIZE, MIME_TYPE, DURATION, HEIGHT, WIDTH
            )
        }
        val srcUri = try {
            srcUriStr.toUriOrThrow()
        } catch (e: Exception) {
            Timber.i(e, "Can't parse srcUriStr to a valid Uri")
            return MediaSaveResult.Failure.SrcInvalid(e)
        }

        val metaData = UriStorageUtil.resolveMetadata(context, srcUri, queryMetaFields)

        val displayName =
            metaData.displayName ?: UriStorageUtil.getDisplayNameFallback(srcUri) // more robust
            ?: "_" // have to assign a value. It should be ok.

        val storeRawFilename =
            messageFilePathUseCase.generateFilenameByPrependTime(messageTimestampMs, displayName)
        val mimeType = metaData.mimeType ?: UriStorageUtil.getMimeTypeFallback(context, srcUri)
        val (calcTgtUrl, failure) = calcRawSharedStorageUri(
            userId = userId,
            topicId = topicId,
            messageTimestampMs = messageTimestampMs,
            rawFilename = storeRawFilename,
            mimeType = mimeType
        )
        if (failure != null) {
            return failure
        }
        val rawFileTgtUri =
            requireNotNull(calcTgtUrl) { "save Target Uri is null on failure not null" }

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
                -> return MediaSaveResult.Failure.SrcInvalid(copyResult.cause)

            is FileCopyResult.Error.TgtNotFound,
            is FileCopyResult.Error.TgtPermissionDenied,
                -> return MediaSaveResult.Failure.TgtInvalid(copyResult.cause)

            is FileCopyResult.Error.TgtOpenUnexpected,
            is FileCopyResult.Error.CopyIOError,
            is FileCopyResult.Error.CopyUnexpected,
                -> return MediaSaveResult.Failure.Unexpected(
                IllegalStateException(copyResult.cause)
            )
        }
        // Following logics will be different on different mime
        val isImage = mimeType.startsWith("image")
        val isVideo = mimeType.startsWith("video")
        val isAudio = mimeType.startsWith("audio")

        // generate thumbnail
        val thumbnailFilename = if (isImage || isVideo) {
            val name = messageFilePathUseCase.generateFilenameByPrependTime(
                messageTimestampMs, "thumb.webp"
            )
            val uriCandidates = listOf("src" to srcUri, "tgt" to rawFileTgtUri)
            val (isOK, lastError) = generateImageOrVideoThumbnailFromSrcCandidates(
                userId = userId,
                topicId = topicId,
                messageTimestampMs = messageTimestampMs,
                thumbnailFilename = name,
                srcUriCandidates = uriCandidates
            )
            if (!isOK) {
                return MediaSaveResult.Failure.Unexpected(lastError!!) // Exit quickly when failed
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
        return MediaSaveResult.Success(
            SavedMedia(
                filename = storeRawFilename,
                originalFilename = displayName,
                fileSize = fileSize,
                thumbnailFilename = thumbnailFilename,
                mimeType = mimeType,
                duration = finalDimensionAndDuration.duration,
                width = finalDimensionAndDuration.width,
                height = finalDimensionAndDuration.height
            )
        )
    }

    private suspend fun generateImageOrVideoThumbnailFromSrcCandidates(
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
                context,
                tryUri,
                targetFile = thumbnailFile,
                maxWidth = THUMBNAIL_MAX_WIDTH,
                maxHeight = THUMBNAIL_MAX_HEIGHT
            )) {
                is GenerateThumbnailResult.Success -> {
                    return true to null
                }

                is GenerateThumbnailResult.Error -> {
                    Timber.i(
                        generateThumbResult.cause, "Generate thumbnail failed for $uriName Uri "
                    )
                    generateThumbnailLastError = generateThumbResult.cause
                }
            }
        }
        return false to generateThumbnailLastError
    }

    private fun calcThumbnailFile(
        userId: UserId,
        topicId: TopicId,
        messageTimestampMs: Long,
        thumbnailFilename: String,
    ): File {
        val thumbnailFullRelativePath = messageFilePathUseCase.generateFullRelativePath(
            userId = userId,
            topicId = topicId,
            timestampMs = messageTimestampMs,
            filename = thumbnailFilename
        )
        return thumbnailRootDir.resolveSubPaths(
            thumbnailFullRelativePath, true, lastPathIsFile = true
        )
    }

    private suspend fun calcRawSharedStorageUri(
        userId: UserId,
        topicId: TopicId,
        messageTimestampMs: Long,
        rawFilename: String,
        mimeType: String,
    ): Pair<Uri?, MediaSaveResult.Failure?> {
        val tgtRootTreeUri = getSharedStorageRootUriWithCache(userId)
            ?: return null to MediaSaveResult.Failure.TgtInvalid(
                IllegalStateException("messageFileRootUri is Null")
            )
        val relativePathSegments = messageFilePathUseCase.generateFullRelativePath(
            userId, topicId, messageTimestampMs, rawFilename
        )
        when (val result = UriStorageUtil.ensureSAFFileUri(
            context, tgtRootTreeUri, relativePathSegments, mimeType
        )) {
            is FindOrCreateFileUriResult.Success -> return result.uri to null
            // Will return on errors
            is FindOrCreateFileUriResult.Error.PermissionDenied -> return null to MediaSaveResult.Failure.TgtInvalid(
                result.cause
            )

            is FindOrCreateFileUriResult.Error.Unexpected -> return null to MediaSaveResult.Failure.Unexpected(
                result.cause
            )

            is FindOrCreateFileUriResult.NotFound -> return null to MediaSaveResult.Failure.Unexpected(
                IllegalStateException("EnsureSAFFile got Not Found")
            )
        }
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
                    initialMeta.width, initialMeta.height, initialMeta.duration
                )
            }

            isVideo && !(isDimensionValid(
                initialMeta.width, initialMeta.height
            ) && isDurationValid) -> {
                UriStorageUtil.getVideo3DimensionsFallback(context, mediaUri)
                    ?.let {
                        DimensionAndDuration(it.width, it.height, it.duration)
                    } ?: DimensionAndDuration(
                    initialMeta.width, initialMeta.height, initialMeta.duration
                )
            }

            isAudio && !isDurationValid -> {
                val d = UriStorageUtil.getAudioDurationFallback(context, mediaUri)
                    ?: initialMeta.duration
                DimensionAndDuration(null, null, d)
            }

            else -> DimensionAndDuration(
                initialMeta.width, initialMeta.height, initialMeta.duration
            )
        }
    }
}