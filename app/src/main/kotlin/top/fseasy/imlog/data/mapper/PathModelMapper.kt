package top.fseasy.imlog.data.mapper

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import top.fseasy.imlog.data.util.MimeTypeUtils
import top.fseasy.imlog.data.util.toFileProviderUri
import top.fseasy.imlog.domain.model.AbsolutePathModel


/**
 * NOTE: we can't define SharedStorageOnly mapper here, as it needs the uri root of user,
 * which can't be accessed in public.
 * And I think it's unnecessary to put it here by some hack ways like creating a public api.
 * The uri file should be mainly used in that class internal.
 * So we put all the StoragePathModel mapper to the StorageRepositoryImpl
 * @see top.fseasy.imlog.data.repository.StorageRepositoryImpl
 */


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
 * No exception will be thrown (instead, a default value will be given on error)
 * Run in IO.
 */
suspend fun AbsolutePathModel.getMimeType(context: Context): String = when (this) {
    is AbsolutePathModel.UriStrModel -> this.getMimeType(context)
    is AbsolutePathModel.FileModel -> this.getMimeType()
}

/**
 * No exception will be thrown (instead, a default value will be given on error)
 * Run in IO.
 */
suspend fun AbsolutePathModel.UriStrModel.getMimeType(context: Context) = this.value.toUriOrNull()
    ?.let { MimeTypeUtils.getMimeType(context, it) } ?: MimeTypeUtils.getErrorDefaultMimeType()

/**
 * No exception will be thrown (instead, a default value will be given on error)
 * Run in IO.
 */
suspend fun AbsolutePathModel.FileModel.getMimeType() = MimeTypeUtils.getMimeType(this.value)

