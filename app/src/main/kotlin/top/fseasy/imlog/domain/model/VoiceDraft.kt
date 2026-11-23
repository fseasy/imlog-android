// domain/model/VoiceDraft.kt
package top.fseasy.imlog.domain.model

import kotlin.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.fseasy.imlog.domain.audio.AudioRecordResult
import top.fseasy.imlog.domain.audio.Waveform

data class VoiceDraftDetail(
    val filePath: StoragePathModel.Internal,
    val duration: Duration,
    val waveform: Waveform, // 直接是规范化的浮点波形对象
)

sealed interface VoiceDraft {
  /** Still in Recording, not committed. */
  sealed interface ActiveRecording : VoiceDraft {
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

/**
 * Commit ActiveRecording.Appending & Base -> Committed. If you know it's Base, use the base
 * overloading instead (which didn't need the file operations)
 *
 * Safe for main-thread.
 */
suspend fun VoiceDraft.ActiveRecording.commit(
    recordingResult: AudioRecordResult.Success,
    fileContentAppendOperation:
        suspend (base: StoragePathModel.Internal, newSegment: StoragePathModel.Internal) -> Unit,
    fileDeleteOperation: suspend (StoragePathModel.Internal) -> Unit,
) =
    when (this) {
      is VoiceDraft.ActiveRecording.Appending ->
          withContext(Dispatchers.IO) {
            fileContentAppendOperation(this@commit.base.filePath, recordingFilePath)
            val appendedDetail =
                this@commit.base.copy(
                    duration = this@commit.base.duration + recordingResult.duration,
                    waveform =
                        this@commit.base.waveform.appendSegment(
                            oldDurationSeconds = recordingResult.duration.inWholeSeconds,
                            newRawAmplitudes = recordingResult.amplitudes,
                            newDurationSeconds = recordingResult.duration.inWholeSeconds,
                        ),
                )
            fileDeleteOperation(recordingFilePath)
            VoiceDraft.Committed(appendedDetail)
          }
      is VoiceDraft.ActiveRecording.Base -> this.commit(recordingResult)
    }

fun VoiceDraft.ActiveRecording.Base.commit(
    recordingResult: AudioRecordResult.Success,
): VoiceDraft.Committed {
  return VoiceDraft.Committed(
      detail =
          VoiceDraftDetail(
              filePath = recordingResult.filePath,
              duration = recordingResult.duration,
              waveform =
                  Waveform.fromRawAmplitudes(
                      recordingResult.amplitudes,
                      recordingResult.duration.inWholeSeconds,
                  ),
          ),
  )
}

/** Data -> Domain 边界：忠实反映数据库与反序列化的真实状态 */
sealed interface LoadedVoiceDraft {
  data object None : LoadedVoiceDraft

  data class Valid(val draft: VoiceDraft) : LoadedVoiceDraft

  sealed interface Corrupted : LoadedVoiceDraft {
    data object Json : Corrupted

    // waveform unpack only occurs when base exists
    data class WaveformUnpack(
        val baseFilePath: StoragePathModel.Internal,
        val baseDuration: kotlin.time.Duration,
        val recordingFilePath: StoragePathModel.Internal?,
    ) : Corrupted
  }
}
