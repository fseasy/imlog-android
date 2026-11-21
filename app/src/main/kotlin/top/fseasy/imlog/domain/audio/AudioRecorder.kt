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

sealed interface AudioRecordError {
  /** 录音时间过短，未采集到有效数据 */
  data object TooShort : AudioRecordError

  /** 存储空间不足或中途写满 */
  data object StorageFull : AudioRecordError

  /** 缺少录音权限 */
  data object PermissionDenied : AudioRecordError

  /** 音频焦点被拒或麦克风被其他应用占用 */
  data object MicrophoneBusy : AudioRecordError

  /** 文件创建、IO 读写或输出文件异常 */
  data object FileIo : AudioRecordError

  /** 底层硬件或媒体服务异常 */
  data object RecorderInternal : AudioRecordError
}

class AudioRecordException(
    val error: AudioRecordError,
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message ?: error.toString(), cause) {
  companion object {
    fun fromIllegalStateException(message: String) =
        AudioRecordException(
            error = AudioRecordError.RecorderInternal,
            message = message,
            cause = IllegalStateException(message),
        )
  }
}

sealed interface AudioRecordResult {
  val filePath: StoragePathModel.Internal

  data class Success(
      override val filePath: StoragePathModel.Internal,
      val duration: Duration,
      val amplitudes: FloatArray,
  ) : AudioRecordResult {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as Success

      if (filePath != other.filePath) return false
      if (duration != other.duration) return false
      if (!amplitudes.contentEquals(other.amplitudes)) return false

      return true
    }

    override fun hashCode(): Int {
      var result = filePath.hashCode()
      result = 31 * result + duration.hashCode()
      result = 31 * result + amplitudes.contentHashCode()
      return result
    }
  }

  data class Failure(
      override val filePath: StoragePathModel.Internal,
      val error: AudioRecordError,
  ) : AudioRecordResult
}

data class AudioRecordCancelResult(val filePath: StoragePathModel.Internal)

sealed interface RecordingOsEvent {
  /** 音频焦点丢失（如来电, 内部已自动停止并结算） */
  data object FocusLost : RecordingOsEvent

  /** 磁盘写满，内部已自动停止并结算 */
  data object StorageFull : RecordingOsEvent

  /** 录音中途发生致命错误（如媒体服务崩溃） */
  data object OsNativeError : RecordingOsEvent
}

/**
 * 跨平台录音器通用接口 未来 iOS 可基于 AVAudioEngine / AVAudioRecorder 实现该接口
 *
 * NOTE： AudioRecorder shouldn't delete output file in any condition, leaving it to business layer
 */
interface AudioRecorder : AutoCloseable {
  val snapshot: StateFlow<RecordingSnapshot>

  /**
   * @param onOsEvent callback when recorder get os native event. callback 处理时注意竞态，因为底层事件触发可能连续出现
   *   如磁盘满时，可能连续触发 [RecordingOsEvent.StorageFull] & [RecordingOsEvent.OsNativeError]
   * @throws kotlinx.coroutines.CancellationException
   * @throws AudioRecordException
   */
  suspend fun start(
      destinationPath: StoragePathModel.Internal,
      onOsEvent: (RecordingOsEvent) -> Unit,
  )

  /**
   * NOTE: no matter success or not, we don't delete the result file!
   *
   * @throws kotlinx.coroutines.CancellationException
   * @throws AudioRecordException
   */
  suspend fun stop(): AudioRecordResult

  /**
   * Return with the corresponding `destinationPath` in [start] to let the business layer delete it.
   *
   * @throws kotlinx.coroutines.CancellationException
   * @throws AudioRecordException
   */
  suspend fun cancel(): AudioRecordCancelResult

  /** 音频文件后缀（包含点号，如 ".m4a" 或 ".aac"） */
  val outputAudioFileExtension: String

  /** 音频 MIME 类型，如 "audio/mp4" 或 "audio/aac" */
  val outputAudioMimeType: String
}
