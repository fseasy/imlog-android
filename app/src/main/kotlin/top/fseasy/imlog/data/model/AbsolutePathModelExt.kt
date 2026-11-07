package top.fseasy.imlog.data.model

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import timber.log.Timber
import top.fseasy.imlog.domain.model.AbsolutePathModel
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import androidx.core.net.toUri

/**
 * NO io thread guarantee
 */
fun AbsolutePathModel.syncAppendContent(
  context: Context,
  newSegment: AbsolutePathModel,
): Boolean {
  return runCatching {
    // 1. 先开源：源打不开直接抛异常退出，绝不触碰目标文件
    val input = newSegment.openInputStream(context)
      ?: throw IOException("InputStream null for $newSegment")

    // 2. 利用 input.use 确保后续无论发生任何异常，input 必定被关闭
    input.use { src ->
      val output = openAppendOutputStream(context)
        ?: throw IOException("Append OutputStream null for $this")

      output.use { dst ->
        src.copyTo(dst, DEFAULT_BUFFER_SIZE)
        dst.flush()
      }
    }
  }.fold(
      onSuccess = { true },
      onFailure = { e ->
        when (e) {
          is IOException, is SecurityException, is IllegalArgumentException -> {
            Timber.w(e, "Append $newSegment -> $this failed.")
            false
          }
          else -> throw e // 其他未知系统致命错误不吃掉
        }
      }
  )
}

private fun AbsolutePathModel.openAppendOutputStream(context: Context): OutputStream? =
  when (this) {
    is AbsolutePathModel.AppPathModel -> {
      val file = File(value.value)
      file.parentFile?.mkdirs()
      FileOutputStream(file, true)
    }
    is AbsolutePathModel.UriStrModel -> {
      val uri = Uri.parse(value.value)
      val cr = context.contentResolver

      // 方案 ①：标准 SAF "wa"（追加模式）
      runCatching { cr.openOutputStream(uri, "wa") }.getOrNull()?.let { return it }

      // 方案 ②：降级方案（"rw" 模式保证绝不 truncate，配合 POSIX lseek 移动到文件尾）
      // 全 Android 版本兼容（API 21+），无需判断 SDK_INT
      runCatching {
        val pfd = cr.openFileDescriptor(uri, "rw") ?: return@runCatching null
        try {
          // SEEK_END = 2，直接对 Native FD 进行定位，如果成功则交给 AutoCloseOutputStream 管理
          Os.lseek(pfd.fileDescriptor, 0, OsConstants.SEEK_END)
          ParcelFileDescriptor.AutoCloseOutputStream(pfd)
        } catch (t: Throwable) {
          pfd.close() // 发生任何 seek 失败，立刻关掉防泄露
          throw t
        }
      }.onFailure { Timber.w(it, "fd-seek append fallback failed for $uri") }
          .getOrNull()
    }
  }

private fun AbsolutePathModel.openInputStream(context: Context): InputStream? =
  when (this) {
    is AbsolutePathModel.AppPathModel -> FileInputStream(File(value.value))
    is AbsolutePathModel.UriStrModel ->
      context.contentResolver.openInputStream(value.value.toUri())
  }