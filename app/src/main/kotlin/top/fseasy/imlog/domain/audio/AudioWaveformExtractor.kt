package top.fseasy.imlog.domain.audio

import kotlin.time.Duration
import top.fseasy.imlog.domain.model.StoragePathModel

data class AudioWaveformResult(
    val waveform: Waveform,
    val duration: Duration,
)

interface AudioWaveformExtractor {
  /**
   * Safe for main-thread.
   *
   * @throws kotlinx.coroutines.CancellationException any other exceptions will lead to null result.
   *
   * use [top.fseasy.imlog.data.commonmain.audio.WaveformCodec5Bit] and
   * [top.fseasy.imlog.data.commonmain.audio.WaveformStreamingAccumulator] to do streaming
   * extraction.
   */
  suspend fun extractOrNull(
      path: StoragePathModel,
      knownDuration: Duration? = null,
  ): AudioWaveformResult?
}
