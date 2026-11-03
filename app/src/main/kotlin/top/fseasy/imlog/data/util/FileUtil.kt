package top.fseasy.imlog.data.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import timber.log.Timber
import top.fseasy.imlog.data.constants.FILE_PROVIDER_AUTHORITIES
import top.fseasy.imlog.domain.model.FileDeleteResult

fun File.toFileProviderUri(context: Context): Uri =
    FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITIES, this)

/** Append multiple sub path segments to base File. No io, pure CPU operations. */
fun File.resolve(
    paths: List<String>,
): File = paths.fold(this) { acc, path -> File(acc, path) }

/**
 * Delete file with return FileDeleteResult.
 *
 * Sync version. not run in IO thread.
 */
fun syncDeleteFile(file: File): FileDeleteResult =
    try {
      if (file.exists()) {
        if (file.delete()) {
          FileDeleteResult.Success
        } else {
          FileDeleteResult.Error(
              IllegalStateException("File delete return false without exception")
          )
        }
      } else {
        FileDeleteResult.FileNotExist
      }
    } catch (e: Exception) {
      Timber.d(e, "Delete file $file failed")
      FileDeleteResult.Error(e)
    }

/** Add content to current file */
fun File.appendContent(newSegment: File): Boolean {
  if (!newSegment.exists() || newSegment.length() == 0L) return false

  // 当前文件不存在时，先尝试同挂载点下的 rename（快速路径）
  if (!exists()) {
    parentFile?.mkdirs() // rename 也要求父目录存在
    if (newSegment.renameTo(this)) return true
    // 跨挂载点 rename 失败，继续走 copy 回退
  }

  return try {
    parentFile?.mkdirs() // FileOutputStream 不会自动创建父目录
    FileOutputStream(this, true).use { output ->
      FileInputStream(newSegment).use { input ->
        input.copyTo(output)
      }
    }
    true
  } catch (e: IOException) {
    Timber.w(e, "File Append Content from $newSegment failed(IO error).")
    false
  } catch (e: SecurityException) {
    Timber.w(e, "File Append Content from $newSegment failed(Permission error).")
    false
  }
}
