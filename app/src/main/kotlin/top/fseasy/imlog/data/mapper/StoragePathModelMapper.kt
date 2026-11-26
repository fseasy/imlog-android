package top.fseasy.imlog.data.mapper

import android.content.Context
import android.net.Uri
import java.io.File
import java.nio.file.InvalidPathException
import java.nio.file.Path
import top.fseasy.imlog.common.runSuspendCatching
import top.fseasy.imlog.data.util.FindOrCreateFileUriResult
import top.fseasy.imlog.data.util.UriPathUtil
import top.fseasy.imlog.data.util.resolve
import top.fseasy.imlog.domain.PathResolveException
import top.fseasy.imlog.domain.model.AbsolutePathModel
import top.fseasy.imlog.domain.model.InternalStorageRootSource
import top.fseasy.imlog.domain.model.SharedStorageRootSource
import top.fseasy.imlog.domain.model.StoragePathModel
import top.fseasy.imlog.domain.model.UserId

fun InternalStorageRootSource.toFile(context: Context): File {
  return when (this) {
    InternalStorageRootSource.Persistent -> context.filesDir
    InternalStorageRootSource.Cache -> context.cacheDir
  }
}

/**
 * pure cpu operations.
 *
 * @throws PathResolveException
 */
fun StoragePathModel.Internal.toNioPathOrThrow(context: Context): Path =
    try {
      root.toFile(context).resolve(fullRelativePath).toPath()
    } catch (e: InvalidPathException) {
      throw PathResolveException(e.message ?: "resolve path $this failed")
    }

/**
 * To absolute path model without creating dirs/files. pure cpu operations.
 *
 * @throws top.fseasy.imlog.domain.PathResolveException
 */
fun StoragePathModel.Internal.toAbsolutePathWithoutCreating(context: Context) =
    AbsolutePathModel.AppPathModel(toNioPathOrThrow(context).toAppPath())

/**
 * Run in IO thread for io parts.
 *
 * @param userRootUriProvider
 *   see @top.fseasy.imlog.data.repository.StorageRepositoryImpl.getSharedStorageRootUriWithCache
 * @throws PathResolveException
 * @throws kotlinx.coroutines.CancellationException
 */
suspend fun SharedStorageRootSource.toUriOrThrow(userRootUriProvider: suspend (UserId) -> Uri?) =
    when (this) {
      is SharedStorageRootSource.Direct -> uriStr.toUri()
      is SharedStorageRootSource.LookupByUser ->
          runSuspendCatching { userRootUriProvider(userId) }
              .getOrElse { e ->
                throw PathResolveException(
                    "Get Root URI for  current user $userId get exception: ${e.message}",
                    e,
                )
              } ?: throw PathResolveException("Storage root URI for current user $userId is null.")
    }

/**
 * Ensure file uri: find or create file (+ mimeType) uri for the given path and root uri. Run in IO
 * for io parts.
 *
 * @throws Exception
 */
suspend fun StoragePathModel.Shared.ensureFileUri(
    userRootUriProvider: suspend (UserId) -> Uri?,
    context: Context,
    mimeType: String,
): Uri {
  val rootUri = this.root.toUriOrThrow(userRootUriProvider)
  return when (
      val result =
          UriPathUtil.ensureSAFFileUri(
              context = context,
              rootTreeUri = rootUri,
              relativePathSegments = this.fullRelativePath,
              fileMimeType = mimeType,
          )
  ) {
    is FindOrCreateFileUriResult.Success -> result.uri
    is FindOrCreateFileUriResult.NotFound ->
        throw IllegalStateException("Get NotFound while calling ensureUri")
    is FindOrCreateFileUriResult.Error -> throw result.cause
  }
}

/**
 * Ensure file uri: find or create dir uri for the given path and root uri. Run in IO for io parts.
 *
 * @throws Exception
 */
suspend fun StoragePathModel.Shared.ensureDirectorUri(
    userRootUriProvider: suspend (UserId) -> Uri?,
    context: Context,
): Uri {
  val rootUri = this.root.toUriOrThrow(userRootUriProvider)
  val result =
      UriPathUtil.ensureSAFDirectoryUri(
          context = context,
          rootTreeUri = rootUri,
          relativePathSegments = this.fullRelativePath,
      )
  return when (result) {
    is FindOrCreateFileUriResult.Success -> result.uri
    is FindOrCreateFileUriResult.NotFound ->
        throw IllegalStateException("Get NotFound while calling ensureUri")
    is FindOrCreateFileUriResult.Error -> throw result.cause
  }
}

/**
 * Find uri: find (only) file (+ mimeType) uri for the given path and root uri. Run in IO for io
 * parts.
 *
 * @throws PathResolveException
 * @throws kotlinx.coroutines.CancellationException
 */
suspend fun StoragePathModel.Shared.findUriOrThrow(
    userRootUriProvider: suspend (UserId) -> Uri?,
    context: Context,
): Uri {
  val rootUri = this.root.toUriOrThrow(userRootUriProvider)
  return when (
      val result =
          UriPathUtil.findSAFUri(
              context = context,
              rootTreeUri = rootUri,
              relativePathSegments = this.fullRelativePath,
          )
  ) {
    is FindOrCreateFileUriResult.Success -> result.uri
    is FindOrCreateFileUriResult.NotFound -> throw PathResolveException("Can not locate uri file")
    is FindOrCreateFileUriResult.Error -> throw PathResolveException("find error", result.cause)
  }
}

/**
 * Transform @StoragePathModel to absolute path model without creating non-existed uri.
 *
 * io parts run in IO.
 *
 * @throws PathResolveException
 * @throws kotlinx.coroutines.CancellationException
 */
suspend fun StoragePathModel.Shared.toAbsolutePathWithoutCreating(
    userRootUriProvider: suspend (UserId) -> Uri?,
    context: Context,
) = AbsolutePathModel.UriStrModel(findUriOrThrow(userRootUriProvider, context = context).toUriStr())

/**
 * Transform @StoragePathModel to absolute path models (DuralWrite will get 2 models!) without
 * creating non-existed uri or files. if this is a StoragePathModel.DuralWrite, will return a list
 * ordering in `[UriStrModel, FileModel]`
 *
 * io parts run in IO.
 *
 * @throws PathResolveException
 * @throws kotlinx.coroutines.CancellationException
 */
suspend fun StoragePathModel.toAbsolutePathsWithoutCreating(
    userRootUriProvider: suspend (UserId) -> Uri?,
    context: Context,
): List<AbsolutePathModel> {

  return when (this) {
    is StoragePathModel.Shared ->
        listOf(toAbsolutePathWithoutCreating(userRootUriProvider, context))
    is StoragePathModel.Internal -> listOf(toAbsolutePathWithoutCreating(context))
    is StoragePathModel.Dual ->
        listOf(
            this.toShared().toAbsolutePathWithoutCreating(userRootUriProvider, context),
            this.toInternal().toAbsolutePathWithoutCreating(context),
        )
  }
}
