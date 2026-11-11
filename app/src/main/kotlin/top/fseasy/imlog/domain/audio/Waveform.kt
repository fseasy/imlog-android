package top.fseasy.imlog.domain.audio

import kotlin.math.roundToInt

class Waveform(val bars: FloatArray) {
  val size: Int
    get() = bars.size

  val isEmpty: Boolean
    get() = bars.isEmpty()

  /** 弹性重采样（供 UI 层调用以适配不同屏幕宽度、横竖屏） */
  fun resample(targetBarCount: Int): FloatArray {
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

  /** 将新音频片段的原始采样追加到当前波形之后 全程直接在 FloatArray 上运算，零编解码消耗 */
  fun appendSegment(
      oldDurationSeconds: Long,
      newRawAmplitudes: FloatArray,
      newDurationSeconds: Long,
  ): Waveform {
    val totalDuration = oldDurationSeconds + newDurationSeconds
    val totalTargetBars = calculateTargetBars(totalDuration)

    if (bars.isEmpty() || oldDurationSeconds <= 0) {
      return fromRawAmplitudes(newRawAmplitudes, totalTargetBars)
    }
    if (newRawAmplitudes.isEmpty() || newDurationSeconds <= 0) {
      return this
    }

    // 1. 按时长比例分配柱子数量
    val oldTargetBars =
        ((oldDurationSeconds.toFloat() / totalDuration) * totalTargetBars)
            .roundToInt()
            .coerceIn(1, totalTargetBars - 1)
    val newTargetBars = totalTargetBars - oldTargetBars

    // 2. 旧波形缩放，新波形降采样
    val scaledOldBars = resample(oldTargetBars)
    val newBars = downsample(newRawAmplitudes, newTargetBars)

    return Waveform(scaledOldBars + newBars)
  }

  // 1. 数组内容比对（安全！）
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Waveform) return false
    return bars.contentEquals(other.bars)
  }

  // 2. 数组内容哈希（安全！）
  override fun hashCode(): Int {
    return bars.contentHashCode()
  }

  // 3. 友好日志打印
  override fun toString(): String {
    return "Waveform(size=${bars.size}, preview=${bars.take(5)})"
  }

  companion object {
    val EMPTY = Waveform(FloatArray(0))

    fun calculateTargetBars(durationSeconds: Long): Int {
      return (30 + durationSeconds).toInt().coerceIn(30, 100)
    }

    /** 从高频采样降采样到目标柱数 */
    fun fromRawAmplitudes(rawAmplitudes: FloatArray, targetBars: Int): Waveform {
      return Waveform(downsample(rawAmplitudes, targetBars))
    }

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
