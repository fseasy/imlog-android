package top.fseasy.imlog.domain.recorder

import kotlin.time.Duration
import kotlinx.coroutines.flow.StateFlow
import top.fseasy.imlog.domain.model.StoragePathModel

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
  }
}

sealed interface AudioRecordResult {
  val filePath: StoragePathModel.Internal

  data class Success(
      override val filePath: StoragePathModel.Internal,
      val duration: Duration,
      val waveform: List<Float>,
  ) : AudioRecordResult

  data class Failure(override val filePath: StoragePathModel.Internal) : AudioRecordResult
}

/**
 * 跨平台录音器通用接口 未来 iOS 可基于 AVAudioEngine / AVAudioRecorder 实现该接口
 *
 * NOTE： AudioRecorder shouldn't delete output file in any condition, leaving it to business layer
 */
interface AudioRecorder : AutoCloseable {
  val snapshot: StateFlow<RecordingSnapshot>

  suspend fun start(destinationPath: StoragePathModel.Internal): Result<Unit>

  suspend fun stop(): AudioRecordResult?

  /** Return the failure with given `destinationPath` in [start] to let the business layer delete it. */
  suspend fun cancel(): AudioRecordResult.Failure?

  /** 音频文件后缀（包含点号，如 ".m4a" 或 ".aac"） */
  val outputAudioFileExtension: String

  /** 音频 MIME 类型，如 "audio/mp4" 或 "audio/aac" */
  val outputAudioMimeType: String
}
