package top.fseasy.imlog.data.mapper

import android.content.Context
import android.net.Uri
import top.fseasy.imlog.data.repository.StorageRepositoryImpl
import top.fseasy.imlog.data.util.MimeTypeUtils
import top.fseasy.imlog.data.util.toFileProviderUri
import top.fseasy.imlog.domain.model.AbsolutePathModel
import top.fseasy.imlog.domain.model.StoragePathModel

/**
 * Transform the AbsolutePathModel to Uri.
 * - if it's File, will use the FileProvider to transform
 * - else for UriStr, just parse it to Uri.
 * NOTE: FileProvider can only handle the allowed scope files. or it will throw exception.
 */
fun AbsolutePathModel.toUri(context: Context): Uri = when (this) {
    is AbsolutePathModel.FileModel -> this.value.toFileProviderUri(context)
    is AbsolutePathModel.UriStrModel -> this.value.toUriOrThrow()
}

/**
 * Get the actual value.
 * Used in condition that supports Any inputs (like coil)
 * @throws Exception if invalid uri
 */
fun AbsolutePathModel.toActualFileOrUri(context: Context): Any = when (this) {
    is AbsolutePathModel.FileModel -> this.value
    is AbsolutePathModel.UriStrModel -> this.value.toUriOrThrow()
}