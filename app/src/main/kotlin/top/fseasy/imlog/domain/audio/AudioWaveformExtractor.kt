package top.fseasy.imlog.domain.audio

import kotlin.time.Duration
import top.fseasy.imlog.domain.model.StoragePathModel

data class AudioWaveformResult(
  val waveform: Waveform,
  val duration: Duration
)

interface AudioWaveformExtractor {
  /** use [WaveformCodec5Bit] and [WaveformStreamingAccumulator] to do streaming extraction. */
  suspend fun extract5BitEncodedWaveformOrNull(
      path: StoragePathModel,
      knownDurationMs: Long? = null,
  ): AudioWaveformResult?
}
