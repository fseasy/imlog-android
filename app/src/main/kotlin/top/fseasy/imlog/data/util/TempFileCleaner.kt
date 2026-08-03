package top.fseasy.imlog.data.util

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import top.fseasy.imlog.di.ApplicationIoScope

/**
 * An autocloseable Temporary file cleaner. Mainly used for ViewModel temp file cleaning.
 *
 * @param externalScope an app level scope so we can delete file independent of viewModelScope
 *   within IO threads. Here we don't inject with Hilt directly (we can), leaving it in ViewModel
 *   level.
 * @see top.fseasy.imlog.features.home.createtopic.CreateTopicViewModel
 */
class TempFileCleaner(@ApplicationIoScope private val externalScope: CoroutineScope) :
    AutoCloseable {

  private val trackedFiles = ConcurrentHashMap.newKeySet<Path>()

  fun track(path: Path) {
    trackedFiles.add(path)
  }

  /** untrack it if you want to manage it yourself, like you'll delete it by yourself */
  fun untrack(path: Path) {
    trackedFiles.remove(path)
  }

  /** untrack and delete the path. */
  fun delete(path: Path) {
    untrack(path)
    externalScope.launch(Dispatchers.IO) {
      runCatching {
        Files.deleteIfExists(path)
      }
    }
  }

  override fun close() {
    val filesToDelete = trackedFiles.toList()
    trackedFiles.clear()

    if (filesToDelete.isEmpty()) return

    externalScope.launch(Dispatchers.IO) {
      filesToDelete.forEach { path ->
        runCatching {
          Files.deleteIfExists(path)
        }
      }
    }
  }
}
