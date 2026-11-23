package top.fseasy.imlog.data.commonmain.audio

import kotlin.math.abs
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import top.fseasy.imlog.domain.audio.AudioWaveformResult
import top.fseasy.imlog.domain.audio.Waveform

class WaveformStreamingAccumulator(
    estimatedDuration: Duration? = null,
) {
  private val predeterminedDuration: Duration? = estimatedDuration?.takeIf {
    it > Duration.ZERO && it.isFinite()
  }

  // 构造时一次性决定策略，热循环内零判断、零 !!、零多余可空字段
  private val sink: AmplitudeSink =
      if (predeterminedDuration != null) {
        FixedBarSink(predeterminedDuration)
      } else {
        DynamicSliceSink()
      }

  private var hasAccumulatedFrames = false
  private var exactPhysicalDurationUs = 0L

  fun accumulate(
      pcmShorts: ShortArray,
      shortCount: Int,
      channelCount: Int,
      sampleRate: Int,
      presentationTimeUs: Long,
  ) {
    if (shortCount <= 0 || channelCount <= 0 || sampleRate <= 0) return

    val frameCount = shortCount / channelCount
    if (frameCount <= 0) return
    hasAccumulatedFrames = true

    val usPerFrame = 1_000_000.0 / sampleRate
    val chunkEndPtsUs = max(0L, presentationTimeUs + (frameCount * usPerFrame).toLong())
    exactPhysicalDurationUs = max(exactPhysicalDurationUs, chunkEndPtsUs)

    var offset = 0
    for (frame in 0 until frameCount) {
      var maxFrameAmp = 0
      for (ch in 0 until channelCount) {
        val sampleVal = abs(pcmShorts[offset++].toInt())
        if (sampleVal > maxFrameAmp) maxFrameAmp = sampleVal
      }
      val normalizedAmp = maxFrameAmp / 32768f
      val currentFramePtsUs = max(0L, presentationTimeUs + (frame * usPerFrame).toLong())
      sink.onFrame(currentFramePtsUs, normalizedAmp)
    }
  }

  fun finish(): AudioWaveformResult? {
    if (!hasAccumulatedFrames) return null

    val finalDurationUs =
        if (exactPhysicalDurationUs > 0L) {
          exactPhysicalDurationUs
        } else {
          predeterminedDuration?.inWholeMicroseconds ?: 0L
        }
    if (finalDurationUs <= 0L) return null

    val finalDuration = finalDurationUs.microseconds
    val bars = sink.buildBars(finalDuration) ?: return null

    return AudioWaveformResult(
        waveform = Waveform(bars),
        duration = finalDuration,
    )
  }

  private sealed interface AmplitudeSink {
    fun onFrame(framePtsUs: Long, normalizedAmp: Float)

    fun buildBars(finalDuration: Duration): FloatArray?
  }

  private class FixedBarSink(duration: Duration) : AmplitudeSink {
    private val durationUs = duration.inWholeMicroseconds.toDouble()
    private val bars = FloatArray(Waveform.calculateTargetBars(duration))

    override fun onFrame(framePtsUs: Long, normalizedAmp: Float) {
      val barIndex = ((framePtsUs / durationUs) * bars.size).toInt().coerceIn(0, bars.lastIndex)
      if (normalizedAmp > bars[barIndex]) {
        bars[barIndex] = normalizedAmp
      }
    }

    override fun buildBars(finalDuration: Duration): FloatArray {
      for (i in bars.indices) {
        bars[i] = bars[i].coerceIn(0.05f, 1.0f)
      }
      return bars
    }
  }

  private class DynamicSliceSink : AmplitudeSink {
    private val amplitudes = ArrayList<Float>(512)
    private var currentWindowIdx = -1L
    private var currentWindowMaxAmp = 0f

    override fun onFrame(framePtsUs: Long, normalizedAmp: Float) {
      val windowIdx = framePtsUs / SLICE_WINDOW_US
      when {
        currentWindowIdx < 0L -> {
          currentWindowIdx = windowIdx
          currentWindowMaxAmp = normalizedAmp
        }
        windowIdx == currentWindowIdx -> {
          if (normalizedAmp > currentWindowMaxAmp) currentWindowMaxAmp = normalizedAmp
        }
        else -> {
          amplitudes.add(currentWindowMaxAmp)
          currentWindowIdx = windowIdx
          currentWindowMaxAmp = normalizedAmp
        }
      }
    }

    override fun buildBars(finalDuration: Duration): FloatArray? {
      if (currentWindowIdx >= 0L) {
        amplitudes.add(currentWindowMaxAmp)
        currentWindowIdx = -1L
      }
      if (amplitudes.isEmpty()) return null
      return Waveform.fromRawAmplitudes(amplitudes.toFloatArray(), finalDuration).bars
    }

    private companion object {
      const val SLICE_WINDOW_US = 50_000L
    }
  }
}
