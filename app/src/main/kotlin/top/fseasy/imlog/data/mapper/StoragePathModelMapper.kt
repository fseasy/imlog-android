package top.fseasy.imlog.data.mapper

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.fseasy.imlog.data.repository.StorageRepositoryImpl
import top.fseasy.imlog.data.util.FindOrCreateFileUriResult
import top.fseasy.imlog.data.util.UriPathUtil
import top.fseasy.imlog.domain.model.AbsolutePathModel
import top.fseasy.imlog.domain.model.InternalLocation
import top.fseasy.imlog.domain.model.SharedStorageRootSource
import top.fseasy.imlog.domain.model.StoragePathModel
import top.fseasy.imlog.domain.model.UserId
import top.fseasy.imlog.domain.util.resolveSubPaths
import java.io.File
import java.io.FileNotFoundException

fun InternalLocation.toFile(context: Context): File {
    return when (this) {
        InternalLocation.Persistent -> context.filesDir
        InternalLocation.Cache -> context.cacheDir
    }
}

/**
 * run in IO
 * @throws SecurityException if permission issue
 */
suspend fun StoragePathModel.InternalOnly.toFileWithCreatingDirectories(context: Context): File =
    withContext(Dispatchers.IO) {
        val rootFile = this@toFileWithCreatingDirectories.internalLocation.toFile(context)
        rootFile.resolveSubPaths(
            this@toFileWithCreatingDirectories.fullRelativePath,
            createDir = true,
            lastPathIsFile = true
        )
    }

/**
 * run in IO. No exception will be thrown.
 */
suspend fun StoragePathModel.InternalOnly.toFileWithoutCreatingDirectories(context: Context): File =
    withContext(Dispatchers.IO) {
        val rootFile = this@toFileWithoutCreatingDirectories.internalLocation.toFile(context)
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
suspend fun StoragePathModel.InternalOnly.createDirectory(context: Context): File =
    withContext(Dispatchers.IO) {
        val rootFile = this@createDirectory.internalLocation.toFile(context)
        rootFile.resolveSubPaths(
            this@createDirectory.fullRelativePath, createDir = true, lastPathIsFile = false
        )
    }


/**
 * Run in IO thread for io parts.
 * @param userRootUriProvider see @top.fseasy.imlog.data.repository.StorageRepositoryImpl.getSharedStorageRootUriWithCache
 * @throws Exception
 */
suspend fun SharedStorageRootSource.toUri(userRootUriProvider: suspend (UserId) -> Uri?) =
    when (this) {
        is SharedStorageRootSource.Direct -> this.uriStr.toUriOrThrow()
        is SharedStorageRootSource.LookupByUser -> userRootUriProvider(this.userId)
            ?: throw IllegalStateException("Storage root URI for current user ${this.userId} is null.")
    }


/** Ensure file uri: find or create file (+ mimeType) uri for the given path and root uri.
 * Run in IO for io parts.
 * @throws Exception
 */
suspend fun StoragePathModel.SharedStorageOnly.ensureFileUri(
    userRootUriProvider: suspend (UserId) -> Uri?,
    context: Context,
    mimeType: String,
): Uri {
    val rootUri = this.root.toUri(userRootUriProvider)
    return when (val result = UriPathUtil.ensureSAFFileUri(
        context = context,
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
suspend fun StoragePathModel.SharedStorageOnly.ensureDirectorUri(
    userRootUriProvider: suspend (UserId) -> Uri?,
    context: Context,
): Uri {
    val rootUri = this.root.toUri(userRootUriProvider)
    val result = UriPathUtil.ensureSAFDirectoryUri(
        context = context, rootTreeUri = rootUri, relativePathSegments = this.fullRelativePath
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
suspend fun StoragePathModel.SharedStorageOnly.findUriOrThrow(
    userRootUriProvider: suspend (UserId) -> Uri?,
    context: Context,
): Uri {
    val rootUri = this.root.toUri(userRootUriProvider)
    return when (val result = UriPathUtil.findSAFUri(
        context = context,
        rootTreeUri = rootUri,
        relativePathSegments = this.fullRelativePath,
    )) {
        is FindOrCreateFileUriResult.Success -> result.uri
        is FindOrCreateFileUriResult.NotFound -> throw FileNotFoundException("Can not locate uri file")
        is FindOrCreateFileUriResult.Error -> throw result.cause
    }
}

/**
 * Transform @StoragePathModel to absolute path models (DuralWrite will get 2 models!)
 * without creating non-existed uri or files.
 * if this is a StoragePathModel.DuralWrite, will return a list ordering in `[UriStrModel, FileModel]`
 *
 * io parts run in IO.
 *
 * @see top.fseasy.imlog.domain.repository.StorageRepository.resolveStoragePathToAbsolutePathsWithoutCreating
 *      it wraps this function and export it to domain level
 *
 * @throws Exception all exceptions came from @StoragePathModel.SharedStorageOnly.findUriOrThrow
 */
suspend fun StoragePathModel.toAbsolutePathModelsWithoutCreating(
    userRootUriProvider: suspend (UserId) -> Uri?,
    context: Context,
): List<AbsolutePathModel> {
    return when (this) {
        is StoragePathModel.SharedStorageOnly -> listOf(
            AbsolutePathModel.UriStrModel(
                this.findUriOrThrow(userRootUriProvider, context = context)
                    .toUriStr()
            )
        )

        is StoragePathModel.InternalOnly -> listOf(
            AbsolutePathModel.FileModel(this.toFileWithoutCreatingDirectories(context))
        )

        is StoragePathModel.DualWrite -> listOf(
            AbsolutePathModel.UriStrModel(
                this.toSharedStorageOnly()
                    .findUriOrThrow(userRootUriProvider, context = context)
                    .toUriStr()
            ), AbsolutePathModel.FileModel(
                this.toInternalOnly()
                    .toFileWithoutCreatingDirectories(context)
            )
        )
    }
}