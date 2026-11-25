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
    val chunkStartPtsUs = max(0L, presentationTimeUs)
    val chunkLastFramePtsUs = max(0L, presentationTimeUs + ((frameCount - 1) * usPerFrame).toLong())
    val chunkEndPtsUs = max(0L, presentationTimeUs + (frameCount * usPerFrame).toLong())
    exactPhysicalDurationUs = max(exactPhysicalDurationUs, chunkEndPtsUs)

    sink.onChunk(
      pcmShorts = pcmShorts,
      frameCount = frameCount,
      channelCount = channelCount,
      usPerFrame = usPerFrame,
      rawPresentationTimeUs = presentationTimeUs,
      chunkStartPtsUs = chunkStartPtsUs,
      chunkLastFramePtsUs = chunkLastFramePtsUs,
    )
  }

  /**
   * @param preferEstimatedDuration 稀疏抽样模式下并未解码到物理文件尾，此时优先使用已知时长。
   */
  fun finish(preferEstimatedDuration: Boolean = false): AudioWaveformResult? {
    if (!hasAccumulatedFrames) return null

    val estimatedUs = predeterminedDuration?.inWholeMicroseconds ?: 0L
    val finalDurationUs =
      when {
        preferEstimatedDuration && estimatedUs > 0L -> max(exactPhysicalDurationUs, estimatedUs)
        exactPhysicalDurationUs > 0L -> exactPhysicalDurationUs
        else -> estimatedUs
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
    fun onChunk(
      pcmShorts: ShortArray,
      frameCount: Int,
      channelCount: Int,
      usPerFrame: Double,
      rawPresentationTimeUs: Long,
      chunkStartPtsUs: Long,
      chunkLastFramePtsUs: Long,
    )

    fun buildBars(finalDuration: Duration): FloatArray?
  }

  private class FixedBarSink(duration: Duration) : AmplitudeSink {
    private val durationUs = duration.inWholeMicroseconds.toDouble()
    private val bars = FloatArray(Waveform.calculateTargetBars(duration))

    override fun onChunk(
      pcmShorts: ShortArray,
      frameCount: Int,
      channelCount: Int,
      usPerFrame: Double,
      rawPresentationTimeUs: Long,
      chunkStartPtsUs: Long,
      chunkLastFramePtsUs: Long,
    ) {
      val startBar = ((chunkStartPtsUs / durationUs) * bars.size).toInt().coerceIn(0, bars.lastIndex)
      val endBar = ((chunkLastFramePtsUs / durationUs) * bars.size).toInt().coerceIn(0, bars.lastIndex)

      if (startBar == endBar) {
        // 快路径：整块落在同一根柱子内，纯 Int 扫描，整块只做 1 次浮点除法
        val totalSamples = frameCount * channelCount
        var maxAmp = 0
        for (i in 0 until totalSamples) {
          val v = abs(pcmShorts[i].toInt())
          if (v > maxAmp) maxAmp = v
        }
        val normalized = maxAmp / 32768f
        if (normalized > bars[startBar]) {
          bars[startBar] = normalized
        }
      } else {
        // 边界路径：仅在跨柱子时逐帧归属，仍按柱子批量提交以减少浮点除法
        var currentBar = -1
        var currentBarMaxInt = 0
        var offset = 0

        for (frame in 0 until frameCount) {
          var maxFrameAmp = 0
          for (ch in 0 until channelCount) {
            val v = abs(pcmShorts[offset++].toInt())
            if (v > maxFrameAmp) maxFrameAmp = v
          }
          val framePtsUs = max(0L, rawPresentationTimeUs + (frame * usPerFrame).toLong())
          val barIndex = ((framePtsUs / durationUs) * bars.size).toInt().coerceIn(0, bars.lastIndex)

          if (barIndex == currentBar) {
            if (maxFrameAmp > currentBarMaxInt) currentBarMaxInt = maxFrameAmp
          } else {
            if (currentBar >= 0) {
              val normalized = currentBarMaxInt / 32768f
              if (normalized > bars[currentBar]) bars[currentBar] = normalized
            }
            currentBar = barIndex
            currentBarMaxInt = maxFrameAmp
          }
        }
        if (currentBar >= 0) {
          val normalized = currentBarMaxInt / 32768f
          if (normalized > bars[currentBar]) bars[currentBar] = normalized
        }
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

    override fun onChunk(
      pcmShorts: ShortArray,
      frameCount: Int,
      channelCount: Int,
      usPerFrame: Double,
      rawPresentationTimeUs: Long,
      chunkStartPtsUs: Long,
      chunkLastFramePtsUs: Long,
    ) {
      val startWindow = chunkStartPtsUs / SLICE_WINDOW_US
      val endWindow = chunkLastFramePtsUs / SLICE_WINDOW_US

      if (startWindow == endWindow) {
        val totalSamples = frameCount * channelCount
        var maxAmp = 0
        for (i in 0 until totalSamples) {
          val v = abs(pcmShorts[i].toInt())
          if (v > maxAmp) maxAmp = v
        }
        commitWindow(startWindow, maxAmp / 32768f)
      } else {
        var localWindowIdx = -1L
        var localWindowMaxInt = 0
        var offset = 0

        for (frame in 0 until frameCount) {
          var maxFrameAmp = 0
          for (ch in 0 until channelCount) {
            val v = abs(pcmShorts[offset++].toInt())
            if (v > maxFrameAmp) maxFrameAmp = v
          }
          val framePtsUs = max(0L, rawPresentationTimeUs + (frame * usPerFrame).toLong())
          val windowIdx = framePtsUs / SLICE_WINDOW_US

          if (windowIdx == localWindowIdx) {
            if (maxFrameAmp > localWindowMaxInt) localWindowMaxInt = maxFrameAmp
          } else {
            if (localWindowIdx >= 0L) {
              commitWindow(localWindowIdx, localWindowMaxInt / 32768f)
            }
            localWindowIdx = windowIdx
            localWindowMaxInt = maxFrameAmp
          }
        }
        if (localWindowIdx >= 0L) {
          commitWindow(localWindowIdx, localWindowMaxInt / 32768f)
        }
      }
    }

    private fun commitWindow(windowIdx: Long, normalizedAmp: Float) {
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