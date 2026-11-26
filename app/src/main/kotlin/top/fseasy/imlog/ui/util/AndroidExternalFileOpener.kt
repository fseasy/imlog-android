package top.fseasy.imlog.ui.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ActivityContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import top.fseasy.imlog.R
import top.fseasy.imlog.data.mapper.toUri
import top.fseasy.imlog.data.util.MimeTypeUtils
import top.fseasy.imlog.domain.model.StoragePathModel
import top.fseasy.imlog.domain.repository.StorageRepository
import top.fseasy.imlog.ui.commonmain.ExternalFileOpener

class AndroidExternalFileOpener
@Inject
constructor(
    @ActivityContext private val context: Context,
    private val storageRepository: StorageRepository,
) : ExternalFileOpener {

  override suspend fun openSharedAudio(path: StoragePathModel.Shared): Boolean {
    val uri = resolveUriOrNull(path) ?: return false

    // 1. 优先指定 "audio/*" 用系统播放器打开（避免损坏的 .aac 文件扩展名识别失败）
    return tryOpenWithChooser(
        uri = uri,
        mimeType = "audio/*",
    ) || tryShareFallback(uri = uri, mimeType = "audio/*")
  }

  override suspend fun openSharedFile(path: StoragePathModel.Shared): Boolean {
    val uri = resolveUriOrNull(path) ?: return false

    // 传 null 让 openFileWithChooser 自动走 contentResolver.getType -> MimeTypeUtils -> "*/*"
    return tryOpenWithChooser(
        uri = uri,
        mimeType = null,
    ) || tryShareFallback(uri = uri, mimeType = "*/*")
  }

  private suspend fun resolveUriOrNull(path: StoragePathModel.Shared): Uri? {
    return try {
      storageRepository
          .resolveSharedPathOrThrow(path)
          .value
          .toUri()
    } catch (e: Exception) {
      Timber.w(e, "Failed to resolve shared storage path: $path")
      null
    }
  }

  private suspend fun tryOpenWithChooser(
      uri: Uri,
      mimeType: String?,
  ): Boolean {
    return try {
      openFileWithChooser(
          context = context,
          uri = uri,
          mimeType = mimeType,
      )
      true
    } catch (e: CancellationException) {
      throw e
    } catch (e: ActivityNotFoundException) {
      Timber.w(e, "No app found to view uri=$uri, mimeType=$mimeType")
      false
    } catch (e: Exception) {
      Timber.w(e, "Failed to open file with chooser: uri=$uri")
      false
    }
  }

  /** 若没有任何应用支持 ACTION_VIEW，降级为系统分享/导出，方便把损坏或特殊文件传给其他应用 */
  private fun tryShareFallback(
      uri: Uri,
      mimeType: String,
  ): Boolean {
    return try {
      val shareIntent =
          Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          }
      val chooser =
          Intent.createChooser(
            shareIntent,
            context.getString(R.string.prefix_open_file_by_chooser_with_name, ""),
              )
              .apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
              }
      context.startActivity(chooser)
      true
    } catch (e: Exception) {
      Timber.w(e, "Share fallback failed: uri=$uri")
      false
    }
  }
}

/**
 * Create chooserIntent to open Uri file. Safe to call from main-thread.
 *
 * @throws ActivityNotFoundException No app found to open the chooser/file
 * @throws SecurityException
 * @throws Exception other exceptions
 */
suspend fun openFileWithChooser(
    context: Context,
    uri: Uri,
    mimeType: String? = null,
    fileDisplayName: String? = null,
) {
  val (resolvedMimeType, resolvedDisplayName) =
      withContext(Dispatchers.IO) {
        val detectedMime =
            mimeType?.takeIf { it.isNotBlank() }
                ?: context.contentResolver.getType(uri)?.takeUnless {
                  it == "application/octet-stream" || it == "*/*"
                }
                ?: MimeTypeUtils.getMimeTypeOrNull(context, uri)
                ?: "*/*"

        val displayName =
            fileDisplayName?.takeIf { it.isNotBlank() } ?: queryDisplayNameOrNull(context, uri)

        detectedMime to displayName
      }

  val viewIntent =
      Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, resolvedMimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }

  val chooserTitle: CharSequence? =
      if (!resolvedDisplayName.isNullOrBlank()) {
        context.getString(R.string.prefix_open_file_by_chooser_with_name, resolvedDisplayName)
      } else {
        null // 拿不到文件名时退回系统默认选择器标题（如 "Open with"），避免出现空白占位符
      }

  val chooserIntent =
      Intent.createChooser(viewIntent, chooserTitle).apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (context !is Activity) {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      }

  withContext(Dispatchers.Main.immediate) {
    context.startActivity(chooserIntent)
  }
}

private fun queryDisplayNameOrNull(context: Context, uri: Uri): String? {
  if (uri.scheme == ContentResolver.SCHEME_FILE) {
    return uri.lastPathSegment
  }
  if (uri.scheme != ContentResolver.SCHEME_CONTENT) return null

  return try {
    context.contentResolver
        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
          if (cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx != -1) cursor.getString(idx) else null
          } else null
        }
  } catch (_: Exception) {
    null
  }
}
