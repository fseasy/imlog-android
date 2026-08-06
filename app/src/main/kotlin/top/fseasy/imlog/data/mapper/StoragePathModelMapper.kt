package top.fseasy.imlog.data.mapper

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Path
import top.fseasy.imlog.data.util.FindOrCreateFileUriResult
import top.fseasy.imlog.data.util.UriPathUtil
import top.fseasy.imlog.data.util.resolve
import top.fseasy.imlog.domain.model.AbsolutePathModel
import top.fseasy.imlog.domain.model.InternalLocation
import top.fseasy.imlog.domain.model.SharedStorageRootSource
import top.fseasy.imlog.domain.model.StoragePathModel
import top.fseasy.imlog.domain.model.UserId

fun InternalLocation.toFile(context: Context): File {
  return when (this) {
    InternalLocation.Persistent -> context.filesDir
    InternalLocation.Cache -> context.cacheDir
  }
}

/** pure cpu operations. */
fun StoragePathModel.InternalOnly.toNioPath(context: Context): Path =
    internalLocation.toFile(context).resolve(fullRelativePath).toPath()

/** To absolute path model without creating dirs/files. pure cpu operations. */
fun StoragePathModel.InternalOnly.toAbsolutePathWithoutCreating(context: Context) =
    AbsolutePathModel.AppPathModel(toNioPath(context).toAppPath())

/**
 * Run in IO thread for io parts.
 *
 * @param userRootUriProvider
 *   see @top.fseasy.imlog.data.repository.StorageRepositoryImpl.getSharedStorageRootUriWithCache
 * @throws Exception
 */
suspend fun SharedStorageRootSource.toUri(userRootUriProvider: suspend (UserId) -> Uri?) =
    when (this) {
      is SharedStorageRootSource.Direct -> uriStr.toUriOrThrow()
      is SharedStorageRootSource.LookupByUser ->
          userRootUriProvider(userId)
              ?: throw IllegalStateException("Storage root URI for current user $userId is null.")
    }

/**
 * Ensure file uri: find or create file (+ mimeType) uri for the given path and root uri. Run in IO
 * for io parts.
 *
 * @throws Exception
 */
suspend fun StoragePathModel.SharedStorageOnly.ensureFileUri(
    userRootUriProvider: suspend (UserId) -> Uri?,
    context: Context,
    mimeType: String,
): Uri {
  val rootUri = this.root.toUri(userRootUriProvider)
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
suspend fun StoragePathModel.SharedStorageOnly.ensureDirectorUri(
    userRootUriProvider: suspend (UserId) -> Uri?,
    context: Context,
): Uri {
  val rootUri = this.root.toUri(userRootUriProvider)
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
 * @throws Exception
 * @throws FileNotFoundException
 */
suspend fun StoragePathModel.SharedStorageOnly.findUriOrThrow(
    userRootUriProvider: suspend (UserId) -> Uri?,
    context: Context,
): Uri {
  val rootUri = this.root.toUri(userRootUriProvider)
  return when (
      val result =
          UriPathUtil.findSAFUri(
              context = context,
              rootTreeUri = rootUri,
              relativePathSegments = this.fullRelativePath,
          )
  ) {
    is FindOrCreateFileUriResult.Success -> result.uri
    is FindOrCreateFileUriResult.NotFound -> throw FileNotFoundException("Can not locate uri file")
    is FindOrCreateFileUriResult.Error -> throw result.cause
  }
}

/**
 * Transform @StoragePathModel to absolute path model without creating non-existed uri.
 *
 * io parts run in IO.
 *
 * @throws Exception came from @StoragePathModel.SharedStorageOnly.findUriOrThrow
 */
suspend fun StoragePathModel.SharedStorageOnly.toAbsolutePathWithoutCreating(
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
 * @throws Exception all exceptions came from @StoragePathModel.SharedStorageOnly.findUriOrThrow
 * @see
 *   top.fseasy.imlog.domain.repository.StorageRepository.resolveStoragePathToAbsolutePathsWithoutCreating
 *   it wraps this function and export it to domain level
 */
suspend fun StoragePathModel.toAbsolutePathModelsWithoutCreating(
    userRootUriProvider: suspend (UserId) -> Uri?,
    context: Context,
): List<AbsolutePathModel> {

  return when (this) {
    is StoragePathModel.SharedStorageOnly ->
        listOf(toAbsolutePathWithoutCreating(userRootUriProvider, context))
    is StoragePathModel.InternalOnly -> listOf(toAbsolutePathWithoutCreating(context))
    is StoragePathModel.DualWrite ->
        listOf(
            this.toSharedStorageOnly().toAbsolutePathWithoutCreating(userRootUriProvider, context),
            this.toInternalOnly().toAbsolutePathWithoutCreating(context),
        )
  }
}
