package top.fseasy.imlog.ui.commonmain

import androidx.compose.runtime.staticCompositionLocalOf
import top.fseasy.imlog.domain.model.StoragePathModel

interface ExternalFileOpener {
  /** @return true 表示成功拉起外部应用，false 表示未找到可用应用或打开失败 */
  suspend fun openSharedAudio(path: StoragePathModel.Shared): Boolean
  suspend fun openSharedFile(path: StoragePathModel.Shared): Boolean
}

val LocalExternalFileOpener =
    staticCompositionLocalOf<ExternalFileOpener> {
      error("ExternalFileOpener not provided")
    }
