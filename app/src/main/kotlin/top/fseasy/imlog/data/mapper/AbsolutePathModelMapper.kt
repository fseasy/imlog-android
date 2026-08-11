package top.fseasy.imlog.data.mapper

import android.content.Context
import android.net.Uri
import top.fseasy.imlog.data.util.toFileProviderUri
import top.fseasy.imlog.domain.model.AbsolutePathModel

/**
 * Transform the AbsolutePathModel to Uri.
 * - if it's File, will use the FileProvider to transform. NOTE: FileProvider can only handle the
 *   allowed scope files. or it will throw exception.
 * - else for UriStr, just parse it to Uri.
 */
fun AbsolutePathModel.toUri(context: Context): Uri =
    when (this) {
      is AbsolutePathModel.AppPathModel -> this.value.toFile().toFileProviderUri(context)

      is AbsolutePathModel.UriStrModel -> this.value.toUriOrThrow()
    }

/**
 * Get the actual value.
 *
 * Used in condition that supports Any inputs (like coil)
 *
 * @throws Exception if invalid uri
 */
fun AbsolutePathModel.toActualFileOrUri(): Any =
    when (this) {
      is AbsolutePathModel.AppPathModel -> this.value.toFile()
      is AbsolutePathModel.UriStrModel -> this.value.toUriOrThrow()
    }


