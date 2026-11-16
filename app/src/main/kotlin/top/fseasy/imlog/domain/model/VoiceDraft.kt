// domain/model/VoiceDraft.kt
package top.fseasy.imlog.domain.model

import kotlin.time.Duration
import top.fseasy.imlog.domain.audio.Waveform

data class VoiceDraftDetail(
    val filePath: StoragePathModel.Internal,
    val duration: Duration,
    val waveform: Waveform, // 直接是规范化的浮点波形对象
)

sealed interface VoiceDraft {
  /** Still in Recording, not committed. */
  sealed interface ActiveRecording: VoiceDraft {
    val recordingFilePath: StoragePathModel.Internal

    /** 尚未产出定稿基线：未开始 / 录制中 / 等待定稿 */
    data class Base(override val recordingFilePath: StoragePathModel.Internal) : ActiveRecording

    /** 在已提交基线之上，有待合并的新片段 */
    data class Appending(
      val base: VoiceDraftDetail,
      override val recordingFilePath: StoragePathModel.Internal,
    ) : ActiveRecording
  }

  /** 已产出定稿基线，可作为追加的 base */
  data class Committed(val detail: VoiceDraftDetail) : VoiceDraft
}
