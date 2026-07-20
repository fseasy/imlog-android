package top.fseasy.imlog.data.repository

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import top.fseasy.imlog.data.mapper.createDirectory
import top.fseasy.imlog.data.mapper.ensureDirectorUri
import top.fseasy.imlog.data.mapper.ensureFileUri
import top.fseasy.imlog.data.mapper.toAbsolutePathModelsWithoutCreating
import top.fseasy.imlog.data.mapper.toFileWithCreatingDirectories
import top.fseasy.imlog.data.mapper.toUri
import top.fseasy.imlog.data.mapper.toUriOrNull
import top.fseasy.imlog.data.mapper.toUriOrThrow
import top.fseasy.imlog.data.mapper.toUriStr
import top.fseasy.imlog.data.util.FileWriteMode
import top.fseasy.imlog.data.util.MetadataResolveUtils
import top.fseasy.imlog.data.util.MimeTypeUtils
import top.fseasy.imlog.data.util.UriPathUtil
import top.fseasy.imlog.data.util.WriteDataResult
import top.fseasy.imlog.data.util.copyBetweenUri
import top.fseasy.imlog.data.util.copyUriToFile
import top.fseasy.imlog.data.util.writeData2Uri
import top.fseasy.imlog.domain.model.AbsolutePathModel
import top.fseasy.imlog.domain.model.AudioMetadata
import top.fseasy.imlog.domain.model.FileCopyResult
import top.fseasy.imlog.domain.model.FileDeleteResult
import top.fseasy.imlog.domain.model.ImageMetadata
import top.fseasy.imlog.domain.model.StoragePathModel
import top.fseasy.imlog.domain.model.UriStr
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.repository.StorageRepository
import top.fseasy.imlog.sqldelight.SqlDelightDb
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
     * currently it's a private function, as related logics are limited here.
     * You can pull it up to interface after changing the return type,
     * or create a new proxy api to avoid unnecessary type transforming.
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

    override suspend fun resolveStoragePathToAbsolutePathsWithoutCreating(storagePath: StoragePathModel): List<AbsolutePathModel> {
        return storagePath.toAbsolutePathModelsWithoutCreating(
            ::getSharedStorageRootUriWithCache,
            context
        )
    }

    override suspend fun getDisplayNameOrDefault(uriStr: UriStr, defaultName: String): String =
        uriStr.toUriOrNull()
            ?.let {
                MetadataResolveUtils.getDisplayNameOrDefault(context, it, defaultName)
            } ?: defaultName

    override suspend fun getAudioMetadataOrNull(filePath: StoragePathModel): AudioMetadata? =
        runCatching {
            resolveStoragePathToAbsolutePathsWithoutCreating(filePath)
        }.getOrNull()
            ?.let { getAudioMetadataOrNull(it.last()) }

    override suspend fun getAudioMetadataOrNull(fileAbsolutePath: AbsolutePathModel): AudioMetadata? =
        MetadataResolveUtils.resolveAudio(fileAbsolutePath, context = context)

    override suspend fun getImageMetadataOrNull(fileAbsolutePath: AbsolutePathModel): ImageMetadata? =
        MetadataResolveUtils.resolveImage(fileAbsolutePath, context)

    /**
     * Run in IO threads for io parts.
     * @throws Exception
     * @return created dir UriStr if filePath contains Uri, else null.
     */
    override suspend fun mkdirs(filePath: StoragePathModel): UriStr? {
        val uri = when (filePath) {
            is StoragePathModel.DualWrite -> {
                filePath.toInternalOnly()
                    .createDirectory(context)
                filePath.toSharedStorageOnly()
                    .ensureDirectorUri(::getSharedStorageRootUriWithCache, context = context)
            }

            is StoragePathModel.SharedStorageOnly -> filePath.ensureDirectorUri(
                ::getSharedStorageRootUriWithCache, context = context
            )

            is StoragePathModel.InternalOnly -> {
                filePath.createDirectory(context)
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
        val file = filePath.toFileWithCreatingDirectories(context)
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
        val fileUri = filePath.ensureFileUri(
            ::getSharedStorageRootUriWithCache, context = context, mimeType = mimeType
        )
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
    ): FileCopyResult =
        runCatching {
            resolveStoragePathToAbsolutePathsWithoutCreating(srcPath)
        }.fold(
            onSuccess = { srcAbsolutePaths ->
                copyFile(
                    srcAbsolutePaths.last(), // Use last to prefer getting local file
                    targetPath = targetPath, srcMimeType = srcMimeType
                )
            },
            onFailure = { e ->
                FileCopyResult.Error.SrcOpenUnexpected(e)
            }
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
            null -> MimeTypeUtils.getMimeType(srcAbsolutePath, context)
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
        val tgtUri = pathModel.ensureFileUri(
            ::getSharedStorageRootUriWithCache, context = context, mimeType = srcMimeType
        )
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
        val tgtFile = pathModel.toFileWithCreatingDirectories(context)
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
            resolveStoragePathToAbsolutePathsWithoutCreating(filePath)
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

}

