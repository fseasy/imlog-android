package top.fseasy.imlog.domain.recorder

import kotlin.time.Clock
import kotlin.time.Duration
import kotlinx.coroutines.flow.StateFlow

/**
 * 录音状态快照（纯 Kotlin，支持 Compose 跨平台）
 *
 * NOTE: we don't report the accumulated waveform in the snapshot.
 */
data class RecordingSnapshot(
    val status: Status = Status.Idle,
    val duration: Duration = Duration.ZERO,
    val currentPulse: Float = 0f,
) {
  enum class Status {
    Idle,
    Recording,
    Paused,
    Stopped,
  }
}

data class AudioRecordResult(
    val filePath: String,
    val duration: Duration,
    val waveform: List<Float>,
)

/** 跨平台录音器通用接口 未来 iOS 可基于 AVAudioEngine / AVAudioRecorder 实现该接口 */
interface AudioRecorder : AutoCloseable {
  val snapshot: StateFlow<RecordingSnapshot>

  suspend fun start(destinationPath: String): Result<Unit>

  suspend fun stop(): AudioRecordResult?

  suspend fun cancel()

  /** 音频文件后缀（包含点号，如 ".m4a" 或 ".aac"） */
  val fileExtension: String

  /** 音频 MIME 类型，如 "audio/mp4" 或 "audio/aac" */
  val mimeType: String

  /** 生成默认文件名（依赖当前实现类实际生效的 [fileExtension]） */
  fun generateDefaultFilename(prefix: String = "recording"): String {
    val timestamp = Clock.System.now().toEpochMilliseconds()
    return "${prefix}_${timestamp}$fileExtension"
  }
}
