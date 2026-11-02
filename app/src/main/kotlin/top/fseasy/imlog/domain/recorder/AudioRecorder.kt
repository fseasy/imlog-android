package top.fseasy.imlog.domain.recorder

import kotlin.time.Duration
import kotlinx.coroutines.flow.StateFlow

/** 录音状态快照（纯 Kotlin，支持 Compose 跨平台） */
data class RecordingSnapshot(
    val status: Status = Status.Idle,
    val duration: Duration = Duration.ZERO,
    val currentPulse: Float = 0f,
    val waveform: List<Float> = emptyList(),
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

  suspend fun pause(): Result<Unit>

  suspend fun resume(): Result<Unit>

  suspend fun stop(): AudioRecordResult?

  suspend fun cancel()
}
