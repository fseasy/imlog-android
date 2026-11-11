// commonMain/src/WaveformStreamingAccumulator.kt
package top.fseasy.imlog.domain.audio

import kotlin.math.abs
import kotlin.math.max
import kotlin.time.Duration.Companion.microseconds

class WaveformStreamingAccumulator(
    private val estimatedDurationUs: Long // 外部已知或探测的时长，<= 0 表示未知
) {
  private val isDurationPreDetermined = estimatedDurationUs > 0L
  private val targetBars =
      if (isDurationPreDetermined) {
        Waveform.calculateTargetBars(estimatedDurationUs / 1_000_000L)
      } else {
        0
      }

  // 分支 A：已知时长的定长桶（仅几十个 Float）
  private val fixedBars = if (isDurationPreDetermined) FloatArray(targetBars) else null

  // 分支 B：未知时长的动态窗口收集器（~几十 KB 内存）
  private val dynamicAmplitudes = if (!isDurationPreDetermined) ArrayList<Float>(512) else null
  private val sliceWindowUs = 50_000L // 50ms 时间窗
  private var currentSliceWindowIdx = -1L
  private var currentSliceMaxAmp = 0f

  // 记录整个音频物理流过的最大时间戳
  private var exactPhysicalDurationUs = 0L

  /**
   * 平台解码器每次吐出 PCM 时调用
   *
   * @param pcmShorts 16-bit PCM 采样数组（交织存放，如 L, R, L, R）
   * @param shortCount 传入的 short 数量
   * @param channelCount 声道数（1 为单声道，2 为立体声）
   * @param sampleRate 采样率（如 44100, 48000）
   * @param presentationTimeUs 当前这一批 PCM 数据的起始 PTS（微秒）
   */
  fun accumulate(
      pcmShorts: ShortArray,
      shortCount: Int,
      channelCount: Int,
      sampleRate: Int,
      presentationTimeUs: Long,
  ) {
    if (shortCount <= 0 || channelCount <= 0 || sampleRate <= 0) return

    val frameCount = shortCount / channelCount
    val usPerFrame = 1_000_000.0 / sampleRate

    // 更新物理实际时长
    val chunkEndPtsUs = presentationTimeUs + (frameCount * usPerFrame).toLong()
    exactPhysicalDurationUs = max(exactPhysicalDurationUs, chunkEndPtsUs)

    var offset = 0
    for (frame in 0 until frameCount) {
      // 1. 多声道合一：取多声道绝对值极大值
      var maxFrameAmp = 0f
      for (ch in 0 until channelCount) {
        val sampleVal = abs(pcmShorts[offset++].toInt())
        if (sampleVal > maxFrameAmp) maxFrameAmp = sampleVal.toFloat()
      }
      val normalizedAmp = maxFrameAmp / 32768f
      val currentFramePts = presentationTimeUs + (frame * usPerFrame).toLong()

      // 2. 映射分桶
      if (isDurationPreDetermined) {
        // 分支 A：映射到已知定长桶
        val barIndex =
            ((currentFramePts.toDouble() / estimatedDurationUs) * targetBars)
                .toInt()
                .coerceIn(0, targetBars - 1)
        if (normalizedAmp > fixedBars!![barIndex]) {
          fixedBars[barIndex] = normalizedAmp
        }
      } else {
        // 分支 B：50ms 窗口动态自适应
        val windowIdx = currentFramePts / sliceWindowUs
        if (windowIdx == currentSliceWindowIdx) {
          if (normalizedAmp > currentSliceMaxAmp) currentSliceMaxAmp = normalizedAmp
        } else {
          if (currentSliceWindowIdx != -1L) {
            dynamicAmplitudes!!.add(currentSliceMaxAmp)
          }
          currentSliceWindowIdx = windowIdx
          currentSliceMaxAmp = normalizedAmp
        }
      }
    }
  }

  /** 解码完毕后调用：打包并输出 5-bit 波形与真实物理时长 */
  fun finish(): AudioWaveformResult? {
    if (exactPhysicalDurationUs <= 0L && estimatedDurationUs <= 0L) {
      return null // 空文件或无效音频
    }

    val finalExactDurationUs =
        if (exactPhysicalDurationUs > 0L) {
          exactPhysicalDurationUs
        } else {
          estimatedDurationUs
        }

    val finalBars =
        if (isDurationPreDetermined) {
          for (i in fixedBars!!.indices) fixedBars[i] = fixedBars[i].coerceIn(0.05f, 1.0f)
          fixedBars
        } else {
          // 动态采样降采样
          val actualDurationSec = max(1L, finalExactDurationUs / 1_000_000L)
          val targetBars = Waveform.calculateTargetBars(actualDurationSec)
          Waveform.fromRawAmplitudes(dynamicAmplitudes!!.toFloatArray(), targetBars).bars
        }

    return AudioWaveformResult(
        waveform = Waveform(finalBars),
        duration = finalExactDurationUs.microseconds,
    )
  }
}
