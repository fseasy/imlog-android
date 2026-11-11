package top.fseasy.imlog.domain.audio

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
      val waveform: FloatArray,
  ) : AudioRecordResult {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as Success

      if (filePath != other.filePath) return false
      if (duration != other.duration) return false
      if (!waveform.contentEquals(other.waveform)) return false

      return true
    }

    override fun hashCode(): Int {
      var result = filePath.hashCode()
      result = 31 * result + duration.hashCode()
      result = 31 * result + waveform.contentHashCode()
      return result
    }
  }

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
