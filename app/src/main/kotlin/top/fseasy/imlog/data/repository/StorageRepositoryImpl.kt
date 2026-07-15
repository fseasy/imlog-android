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
import top.fseasy.imlog.data.mapper.getMimeType
import top.fseasy.imlog.data.mapper.toUri
import top.fseasy.imlog.data.mapper.toUriOrNull
import top.fseasy.imlog.data.mapper.toUriOrThrow
import top.fseasy.imlog.data.mapper.toUriStr
import top.fseasy.imlog.data.util.FileWriteMode
import top.fseasy.imlog.data.util.FindOrCreateFileUriResult
import top.fseasy.imlog.data.util.GenerateThumbnailResult
import top.fseasy.imlog.data.util.MediaFields
import top.fseasy.imlog.data.util.MetadataResolveUtils
import top.fseasy.imlog.data.util.UriPathUtil
import top.fseasy.imlog.data.util.WriteDataResult
import top.fseasy.imlog.data.util.copyBetweenUri
import top.fseasy.imlog.data.util.copyUriToFile
import top.fseasy.imlog.data.util.generateAndSaveThumbnail
import top.fseasy.imlog.data.util.resolveMetadata
import top.fseasy.imlog.data.util.writeData2Uri
import top.fseasy.imlog.domain.model.AbsolutePathModel
import top.fseasy.imlog.domain.model.AudioMetadata
import top.fseasy.imlog.domain.model.FileCopyResult
import top.fseasy.imlog.domain.model.FileDeleteResult
import top.fseasy.imlog.domain.model.InternalLocation
import top.fseasy.imlog.domain.model.SharedStorageRootSource
import top.fseasy.imlog.domain.model.StoragePathModel
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UriStr
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.repository.MediaSaveResult
import top.fseasy.imlog.domain.repository.SavedMedia
import top.fseasy.imlog.domain.repository.StorageRepository
import top.fseasy.imlog.domain.util.resolveSubPaths
import top.fseasy.imlog.sqldelight.SqlDelightDb
import java.io.File
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton
import top.fseasy.imlog.domain.util.deleteFile as deleteFileObject

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

    override suspend fun getDisplayNameOrDefault(uriStr: UriStr, defaultName: String): String =
        uriStr.toUriOrNull()
            ?.let {
                MetadataResolveUtils.getDisplayNameOrDefault(context, it, defaultName)
            } ?: defaultName


    override suspend fun getAudioMetadataOrNull(uriStr: UriStr): AudioMetadata? =
        uriStr.toUriOrNull()
            ?.let {
                MetadataResolveUtils.forAudioUri(context, uri = it)
            }

    /**
     * Run in IO threads for io parts.
     * @throws Exception
     * @return created dir UriStr if filePath contains Uri, else null.
     */
    override suspend fun mkdirs(filePath: StoragePathModel): UriStr? {
        val uri = when (filePath) {
            is StoragePathModel.DualWrite -> {
                filePath.toInternalOnly()
                    .createDirectory(this)
                filePath.toSharedStorageOnly()
                    .ensureDirectorUri(this)
            }

            is StoragePathModel.SharedStorageOnly -> filePath.ensureDirectorUri(this)
            is StoragePathModel.InternalOnly -> {
                filePath.createDirectory(this)
                null
            }
        }
        return uri?.toUriStr()
    }

    /** Run in IO thread in io parts.
     * @param mimeType: set it properly when filePathModel includes Uri.
     * @throws Exception
     * @return sharedStorageUri if filePathModel includes Uri, else null.
     */
    override suspend fun writeFile(
        filePath: StoragePathModel,
        content: ByteArray,
        mimeType: String?,
    ): UriStr? = when (filePath) {
        is StoragePathModel.DualWrite -> {
            val effectiveMimeType =
                requireNotNull(mimeType) { "Must Set mimeType for DualWrite path" }
            writeFileForInternalOnly(filePath.toInternalOnly(), content)
            writeFileForSharedStorageOnly(
                filePath.toSharedStorageOnly(), content, effectiveMimeType
            )
        }

        is StoragePathModel.SharedStorageOnly -> {
            val effectiveMimeType =
                requireNotNull(mimeType) { "Must Set mimeType for SharedStorageOnly path" }
            writeFileForSharedStorageOnly(filePath, content, effectiveMimeType)
        }

        is StoragePathModel.InternalOnly -> {
            writeFileForInternalOnly(filePath, content)
            null
        }
    }

    /** Run in IO thread
     * @throws Exception
     */
    private suspend fun writeFileForInternalOnly(
        filePath: StoragePathModel.InternalOnly,
        content: ByteArray,
    ) {
        val file = filePath.toFileWithCreatingDirectories(this)
        withContext(dispatcher) {
            file.writeBytes(content)
        }
    }

    /** Run in IO in necessary parts.
     * @throws Exception
     */
    private suspend fun writeFileForSharedStorageOnly(
        filePath: StoragePathModel.SharedStorageOnly,
        content: ByteArray,
        mimeType: String,
    ): UriStr {
        val fileUri = filePath.ensureFileUri(this, mimeType)
        when (val r = writeData2Uri(context, tgtFileUri = fileUri, content = content)) {
            is WriteDataResult.Error -> throw r.cause
            is WriteDataResult.Success -> Unit
        }
        return fileUri.toUriStr()
    }

    override suspend fun copyFile(
        srcPath: StoragePathModel,
        targetPath: StoragePathModel,
        srcMimeType: String?,
    ): FileCopyResult = copyFile(
        srcAbsolutePath = srcPath.toAbsolutePathModelsWithoutCreating(this)
            .last(), // Use last to prefer getting local file
        targetPath = targetPath, srcMimeType = srcMimeType
    )

    /**
     * Run in IO.
     *
     * No exception thrown.
     *
     * TODO: optimize for condition input is a File. currently just transform it to Uri.
     *       it's suboptimal in efficiency and tolerance(permission is restricted in FileProvider)
     *
     * @param srcMimeType - it null, will resolve it internal for Uri type target.
     */
    override suspend fun copyFile(
        srcAbsolutePath: AbsolutePathModel,
        targetPath: StoragePathModel,
        srcMimeType: String?,
    ): FileCopyResult {
        suspend fun getEffectiveMimeType(): String = when (srcMimeType) {
            null -> srcAbsolutePath.getMimeType(context)
            else -> srcMimeType
        }

        val unifiedSrcUri = runCatching {
            srcAbsolutePath.toUri(context)
        }.getOrElse {
            return FileCopyResult.Error.SrcOpenUnexpected(
                IllegalArgumentException("Can't Transform $srcAbsolutePath to Uri")
            )
        }
        return when (targetPath) {
            is StoragePathModel.SharedStorageOnly -> copyUriFile2SharedStorage(
                unifiedSrcUri, targetPath, getEffectiveMimeType()
            )

            is StoragePathModel.InternalOnly -> copyUriFile2InternalFile(unifiedSrcUri, targetPath)

            is StoragePathModel.DualWrite -> {
                copyUriFile2SharedStorage(
                    unifiedSrcUri, targetPath.toSharedStorageOnly(), getEffectiveMimeType()
                )
                copyUriFile2InternalFile(unifiedSrcUri, targetPath.toInternalOnly())
            }
        }
    }

    /**
     * Run in IO thread in io parts. No exception will be thrown
     */
    private suspend fun copyUriFile2SharedStorage(
        srcUri: Uri,
        pathModel: StoragePathModel.SharedStorageOnly,
        srcMimeType: String,
    ): FileCopyResult {
        val tgtUri = pathModel.ensureFileUri(this, mimeType = srcMimeType)
        return copyBetweenUri(
            context,
            srcUri,
            tgtUri,
            writeMode = FileWriteMode.WRITE_TRUNCATE,
        )
    }

    /**
     * Run in IO thread in io parts. No exception will be thrown
     */
    private suspend fun copyUriFile2InternalFile(
        srcUri: Uri,
        pathModel: StoragePathModel.InternalOnly,
    ): FileCopyResult {
        val tgtFile = pathModel.toFileWithCreatingDirectories(this)
        return copyUriToFile(context, srcUri, tgtFile)
    }

    /** Run in IO thread in io parts.
     * Swallow all the exceptions.
     */
    override suspend fun deleteFile(
        filePath: AbsolutePathModel,
    ): FileDeleteResult {
        when (filePath) {
            is AbsolutePathModel.UriStrModel -> {
                val uri = filePath.value.toUriOrNull() ?: return FileDeleteResult.Error(
                    IllegalStateException("Invalid uri str: ${filePath.value}")
                )
                return UriPathUtil.deleteUri(context, uri)
            }

            is AbsolutePathModel.FileModel -> {
                return withContext(dispatcher) {
                    deleteFileObject(filePath.value)
                }
            }
        }
    }

    /** Run in IO thread in io parts.
     * Swallow all the exceptions.
     */
    override suspend fun deleteFile(
        filePath: StoragePathModel,
    ): FileDeleteResult {

        val absolutePaths = try {
            filePath.toAbsolutePathModelsWithoutCreating(this)
        } catch (e: FileNotFoundException) {
            Timber.d(e, "deleteFile: Failed to locate file on shared-storage: $filePath")
            return FileDeleteResult.FileNotExist
        } catch (e: Exception) {
            Timber.d(e, "deleteFile: error on locating file on shared-storage: $filePath")
            return FileDeleteResult.Error(e)
        }
        for (path in absolutePaths) {
            when (val deleteResult = deleteFile(path)) {
                is FileDeleteResult.FileNotExist,
                is FileDeleteResult.Error,
                    -> return deleteResult

                else -> Unit
            }
        }
        return FileDeleteResult.Success
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

    companion object {
        /**
         * Run in IO thread for io parts.
         * @throws Exception
         */
        private suspend fun SharedStorageRootSource.toUri(instance: StorageRepositoryImpl) =
            when (this) {
                is SharedStorageRootSource.Direct -> this.uriStr.toUriOrThrow()
                is SharedStorageRootSource.LookupByUser -> instance.getSharedStorageRootUriWithCache(
                    this.userId
                )
                    ?: throw IllegalStateException("Storage root URI for current user ${this.userId} is null.")
            }

        /** Ensure file uri: find or create file (+ mimeType) uri for the given path and root uri.
         * Run in IO for io parts.
         * @throws Exception
         */
        private suspend fun StoragePathModel.SharedStorageOnly.ensureFileUri(
            instance: StorageRepositoryImpl,
            mimeType: String,
        ): Uri {
            val rootUri = this.root.toUri(instance)
            return when (val result = UriPathUtil.ensureSAFFileUri(
                context = instance.context,
                rootTreeUri = rootUri,
                relativePathSegments = this.fullRelativePath,
                fileMimeType = mimeType,
            )) {
                is FindOrCreateFileUriResult.Success -> result.uri
                is FindOrCreateFileUriResult.NotFound -> throw IllegalStateException("Get NotFound while calling ensureUri")
                is FindOrCreateFileUriResult.Error -> throw result.cause
            }
        }

        /** Ensure file uri: find or create dir uri for the given path and root uri.
         * Run in IO for io parts.
         * @throws Exception
         */
        private suspend fun StoragePathModel.SharedStorageOnly.ensureDirectorUri(
            instance: StorageRepositoryImpl,
        ): Uri {
            val rootUri = this.root.toUri(instance)
            val result = UriPathUtil.ensureSAFDirectoryUri(
                context = instance.context,
                rootTreeUri = rootUri,
                relativePathSegments = this.fullRelativePath
            )
            return when (result) {
                is FindOrCreateFileUriResult.Success -> result.uri
                is FindOrCreateFileUriResult.NotFound -> throw IllegalStateException("Get NotFound while calling ensureUri")
                is FindOrCreateFileUriResult.Error -> throw result.cause
            }
        }

        /** Find uri: find (only) file (+ mimeType) uri for the given path and root uri.
         * Run in IO for io parts.
         * @throws Exception
         * @throws FileNotFoundException
         */
        private suspend fun StoragePathModel.SharedStorageOnly.findUriOrThrow(
            instance: StorageRepositoryImpl,
        ): Uri {
            val rootUri = this.root.toUri(instance)
            return when (val result = UriPathUtil.findSAFUri(
                context = instance.context,
                rootTreeUri = rootUri,
                relativePathSegments = this.fullRelativePath,
            )) {
                is FindOrCreateFileUriResult.Success -> result.uri
                is FindOrCreateFileUriResult.NotFound -> throw FileNotFoundException("Can not locate uri file")
                is FindOrCreateFileUriResult.Error -> throw result.cause
            }
        }

        /**
         * run in IO
         * @throws SecurityException if permission issue
         */
        private suspend fun StoragePathModel.InternalOnly.toFileWithCreatingDirectories(instance: StorageRepositoryImpl): File =
            withContext(instance.dispatcher) {
                val rootFile =
                    this@toFileWithCreatingDirectories.internalLocation.toFile(instance.context)
                rootFile.resolveSubPaths(
                    this@toFileWithCreatingDirectories.fullRelativePath,
                    createDir = true,
                    lastPathIsFile = true
                )
            }

        /**
         * run in IO. No exception will be thrown.
         */
        private suspend fun StoragePathModel.InternalOnly.toFileWithoutCreatingDirectories(instance: StorageRepositoryImpl): File =
            withContext(instance.dispatcher) {
                val rootFile =
                    this@toFileWithoutCreatingDirectories.internalLocation.toFile(instance.context)
                rootFile.resolveSubPaths(
                    this@toFileWithoutCreatingDirectories.fullRelativePath,
                    createDir = false,
                    lastPathIsFile = true
                )
            }

        /**
         * run in IO.
         * @throws SecurityException if permission issue
         */
        private suspend fun StoragePathModel.InternalOnly.createDirectory(instance: StorageRepositoryImpl): File =
            withContext(instance.dispatcher) {
                val rootFile = this@createDirectory.internalLocation.toFile(instance.context)
                rootFile.resolveSubPaths(
                    this@createDirectory.fullRelativePath, createDir = true, lastPathIsFile = false
                )
            }

        /**
         * Transform shared path model to absolute path models (DuralWrite will get 2 models!)
         * without creating non-existed uri or files.
         * if this is a StoragePathModel.DuralWrite, will return a list ordering in `[UriStrModel, FileModel]`
         *
         * io parts run in IO.
         *
         * @throws Exception all exceptions came from @StoragePathModel.SharedStorageOnly.findUriOrThrow
         */
        private suspend fun StoragePathModel.toAbsolutePathModelsWithoutCreating(instance: StorageRepositoryImpl): List<AbsolutePathModel> {
            return when (this) {
                is StoragePathModel.SharedStorageOnly -> listOf(
                    AbsolutePathModel.UriStrModel(
                        this.findUriOrThrow(instance)
                            .toUriStr()
                    )
                )

                is StoragePathModel.InternalOnly -> listOf(
                    AbsolutePathModel.FileModel(
                        this.toFileWithoutCreatingDirectories(
                            instance
                        )
                    )
                )

                is StoragePathModel.DualWrite -> listOf(
                    AbsolutePathModel.UriStrModel(
                        this.toSharedStorageOnly()
                            .findUriOrThrow(instance)
                            .toUriStr()
                    ), AbsolutePathModel.FileModel(
                        this.toInternalOnly()
                            .toFileWithoutCreatingDirectories(
                                instance
                            )
                    )
                )
            }
        }
    }
}

private fun InternalLocation.toFile(context: Context): File {
    return when (this) {
        InternalLocation.Persistent -> context.filesDir
        InternalLocation.Cache -> context.cacheDir
    }
}
