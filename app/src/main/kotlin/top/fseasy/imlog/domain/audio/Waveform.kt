package top.fseasy.imlog.domain.audio

import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.DurationUnit

class Waveform(val bars: FloatArray) {
  val size: Int
    get() = bars.size

  val isEmpty: Boolean
    get() = bars.isEmpty()

  /** 弹性重采样（供 UI 层调用以适配不同屏幕宽度、横竖屏） */
  fun resample(targetBarCount: Int): FloatArray {
    if (targetBarCount <= 0) return FloatArray(0)
    if (bars.isEmpty()) return FloatArray(targetBarCount) { 0.1f }
    if (bars.size == targetBarCount) return bars.copyOf()

    val result = FloatArray(targetBarCount)
    val step = (bars.size - 1).toFloat() / (targetBarCount - 1).coerceAtLeast(1)

    for (i in 0 until targetBarCount) {
      val exactIndex = i * step
      val low = exactIndex.toInt()
      val high = (low + 1).coerceAtMost(bars.lastIndex)
      val fraction = exactIndex - low
      result[i] = bars[low] * (1f - fraction) + bars[high] * fraction
    }
    return result
  }

  fun appendWaveform(
      oldDuration: Duration,
      other: Waveform,
      otherDuration: Duration,
  ) =
      appendSegment(
          oldDuration = oldDuration,
          newRawAmplitudes = other.bars,
          newDuration = otherDuration,
      )

  /**
   * 将新音频片段的原始采样追加到当前波形之后。全程直接在 FloatArray 上运算，零编解码消耗。
   *
   * Return a new Waveform instance.
   */
  fun appendSegment(
      oldDuration: Duration,
      newRawAmplitudes: FloatArray,
      newDuration: Duration,
  ): Waveform {
    val totalDuration = oldDuration + newDuration
    val totalTargetBars = calculateTargetBars(totalDuration)

    if (bars.isEmpty() || oldDuration <= Duration.ZERO) {
      return fromRawAmplitudes(newRawAmplitudes, totalTargetBars)
    }
    if (newRawAmplitudes.isEmpty() || newDuration <= Duration.ZERO) {
      return this
    }

    // 1. 按精确时长比例分配柱子数量（避免 < 1s 的片段按整秒截断为 0）
    val oldRatio =
        oldDuration.toDouble(DurationUnit.MILLISECONDS) /
            totalDuration.toDouble(DurationUnit.MILLISECONDS)
    val oldTargetBars = (oldRatio * totalTargetBars).roundToInt().coerceIn(1, totalTargetBars - 1)
    val newTargetBars = totalTargetBars - oldTargetBars

    // 2. 旧波形缩放，新波形降采样
    val scaledOldBars = resample(oldTargetBars)
    val newBars = downsample(newRawAmplitudes, newTargetBars)

    return Waveform(scaledOldBars + newBars)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Waveform) return false
    return bars.contentEquals(other.bars)
  }

  override fun hashCode(): Int {
    return bars.contentHashCode()
  }

  override fun toString(): String {
    return "Waveform(size=${bars.size}, preview=${bars.take(5)})"
  }

  companion object {
    val EMPTY = Waveform(FloatArray(0))

    fun getSilencePlaceholder(duration: Duration) =
        Waveform(FloatArray(calculateTargetBars(duration)) { 0.1f })

    /** 先在 Long 范围限制 [0, 70] 再转 Int，防止超长/INFINITE Duration 溢出. 输出范围 30 ～ 100 */
    fun calculateTargetBars(duration: Duration): Int {
      return 30 + duration.inWholeSeconds.coerceIn(0L, 70L).toInt()
    }

    /** 从高频采样降采样到目标柱数 */
    fun fromRawAmplitudes(rawAmplitudes: FloatArray, targetBars: Int): Waveform {
      return Waveform(downsample(rawAmplitudes, targetBars))
    }

    fun fromRawAmplitudes(rawAmplitudes: FloatArray, duration: Duration) =
        fromRawAmplitudes(rawAmplitudes, calculateTargetBars(duration))

    private fun downsample(rawAmplitudes: FloatArray, targetBars: Int): FloatArray {
      if (rawAmplitudes.isEmpty() || targetBars <= 0) return FloatArray(0)
      val sampled = FloatArray(targetBars)
      val bucketSize = rawAmplitudes.size.toFloat() / targetBars

      for (i in 0 until targetBars) {
        val start = (i * bucketSize).toInt()
        val end = ((i + 1) * bucketSize).toInt().coerceAtMost(rawAmplitudes.size)
        var max = 0f
        if (start < end) {
          for (j in start until end) {
            if (rawAmplitudes[j] > max) max = rawAmplitudes[j]
          }
        } else {
          val idx = start.coerceAtMost(rawAmplitudes.lastIndex)
          max = if (idx >= 0) rawAmplitudes[idx] else 0f
        }
        sampled[i] = max.coerceIn(0.05f, 1f)
      }
      return sampled
    }
  }
}
