package top.fseasy.imlog.data.audio.recorder

import android.content.Context
import android.os.StatFs
import java.io.File
import top.fseasy.imlog.data.mapper.toNioPathOrThrow
import top.fseasy.imlog.domain.model.StoragePathModel

fun syncGetAvailableBytes(dir: File): Long {
  return runCatching { StatFs(dir.absolutePath).availableBytes }.getOrDefault(Long.MAX_VALUE)
}

fun syncIsStorageCriticallyLow(dir: File): Boolean {
  val criticalBytes = 64L * 1024L
  return syncGetAvailableBytes(dir) <= criticalBytes
}

internal data class ResolvedStoragePath(
    val path: StoragePathModel.Internal,
    val file: File,
) {
  companion object {
    /** @throws SecurityException */
    fun syncCreate(path: StoragePathModel.Internal, context: Context): ResolvedStoragePath {
      val file = path.toNioPathOrThrow(context).toFile().apply { parentFile?.mkdirs() }
      return ResolvedStoragePath(path = path, file = file)
    }
  }
}
