package top.fseasy.imlog.ui.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import top.fseasy.imlog.R
import top.fseasy.imlog.data.util.MimeTypeUtils

/**
 * Create chooserIntent to open Uri file
 *
 * @throws ActivityNotFoundException No app found to open the
 * @throws SecurityException
 * @throws Exception other exceptions
 */
suspend fun openFileWithChooser(
    context: Context,
    uri: Uri,
    mimeType: String? = null,
    fileDisplayName: String? = null,
) {
  val resolvedMimeType =
      mimeType
          ?: context.contentResolver.getType(uri)
          ?: MimeTypeUtils.getMimeType(context, uri)
          ?: "*/*" // 保底：让系统列出所有可能打开的应用

  val viewIntent =
      Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, resolvedMimeType)
        // NOTE: grant permission for the 3rd app
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }

  // 3. force selection by createChooser
  val chooserIntent =
      Intent.createChooser(
          viewIntent,
          context.getString(R.string.open_file_by_chooser, fileDisplayName.orEmpty()),
      )
  context.startActivity(chooserIntent)
}
