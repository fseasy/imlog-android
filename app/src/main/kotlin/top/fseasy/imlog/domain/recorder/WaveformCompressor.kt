package top.fseasy.imlog.domain.recorder

import kotlin.io.encoding.Base64
import kotlin.math.roundToInt

object WaveformCodec5Bit {

  /** 1. 动态决定存储柱数（30 ~ 100） 规则：基础 30 根 + 每录制 1 秒增加 1 根，100 根封顶 */
  fun calculateTargetBars(durationSeconds: Long): Int {
    return (30 + durationSeconds).toInt().coerceIn(30, 100)
  }

  /**
   * 压缩：原始 FloatArray -> 5-bit Bit-Packed ByteArray
   *
   * 使用 [calculateTargetBars] 来计算最终存储的 bar 数量
   */
  fun encode(rawAmplitudes: FloatArray, durationSeconds: Long): ByteArray =
    encode(rawAmplitudes, calculateTargetBars(durationSeconds))

  /**
   * 压缩：原始 FloatArray -> 5-bit Bit-Packed ByteArray
   *
   * 数据结构：
   * [ 第0字节: 柱子总数量 N] + [后续字节: N * 5 bits 的紧凑位流]
   */
  fun encode(rawAmplitudes: FloatArray, targetBars: Int): ByteArray {
    if (rawAmplitudes.isEmpty() || targetBars <= 0) return ByteArray(1)
    return packBars(downsample(rawAmplitudes, targetBars))
  }

  /** 3. 解码：5-bit ByteArray -> FloatArray (0.05f ~ 1.0f) 已处理截断 / 恶意数据，避免负位移异常 */
  fun decode(bytes: ByteArray?): FloatArray {
    if (bytes == null || bytes.isEmpty()) return FloatArray(0)

    val count = bytes[0].toInt() and 0xFF
    if (count == 0) return FloatArray(0)

    val result = FloatArray(count)
    var byteIndex = 1
    var bitBuffer = 0
    var bitsInBuffer = 0

    for (i in 0 until count) {
      // 尽量填满至少 5 bit
      while (bitsInBuffer < 5 && byteIndex < bytes.size) {
        bitBuffer = (bitBuffer shl 8) or (bytes[byteIndex].toInt() and 0xFF)
        bitsInBuffer += 8
        byteIndex++
      }

      // 安全处理不足 5 bit 的情况（截断或损坏数据）
      val value =
        if (bitsInBuffer >= 5) {
          val v = (bitBuffer shr (bitsInBuffer - 5)) and 0x1F
          bitsInBuffer -= 5
          v
        } else {
          bitsInBuffer = 0
          0
        }

      result[i] = (value / 31f).coerceIn(0.05f, 1.0f)
    }

    return result
  }

  /** 4. 弹性重采样（适配横屏、大屏、折叠屏） 将任意长度 sourceBars 线性重采样为屏幕需要的 displayBars */
  fun resample(sourceBars: FloatArray, targetBarCount: Int): FloatArray {
    if (sourceBars.isEmpty()) {
      return FloatArray(targetBarCount) { 0.1f }
    }
    if (sourceBars.size == targetBarCount) {
      return sourceBars.copyOf()
    }

    val result = FloatArray(targetBarCount)
    val step = (sourceBars.size - 1).toFloat() / (targetBarCount - 1).coerceAtLeast(1)

    for (i in 0 until targetBarCount) {
      val exactIndex = i * step
      val low = exactIndex.toInt()
      val high = (low + 1).coerceAtMost(sourceBars.lastIndex)
      val fraction = exactIndex - low

      // 线性插值
      result[i] = sourceBars[low] * (1f - fraction) + sourceBars[high] * fraction
    }

    return result
  }

  /**
   * 将新的原始音频片段追加到已压缩的波形数据之后
   *
   * @param oldBytes 已压缩的 5-bit 波形数据
   * @param oldDurationSeconds 已压缩音频的时长（秒）
   * @param newRawAmplitudes 新音频片段的原始振幅列表
   * @param newDurationSeconds 新音频片段的时长（秒）
   * @param targetBars 期望合并后的总 bar 数量；默认 null 时根据合并总时长自动计算
   * @return 合并并重新压缩后的 ByteArray
   */
  fun appendSegment(
    oldBytes: ByteArray?,
    oldDurationSeconds: Long,
    newRawAmplitudes: FloatArray,
    newDurationSeconds: Long,
    targetBars: Int? = null,
  ): ByteArray {
    val totalDuration = oldDurationSeconds + newDurationSeconds
    val totalTargetBars = targetBars ?: calculateTargetBars(totalDuration)

    // 边界情况统一处理
    if (oldBytes == null || oldBytes.isEmpty() || oldDurationSeconds <= 0) {
      return encode(newRawAmplitudes, totalTargetBars)
    }
    if (newRawAmplitudes.isEmpty() || newDurationSeconds <= 0) {
      return oldBytes
    }

    // 1. 按时长占比分配柱数
    val oldTargetBars =
      ((oldDurationSeconds.toFloat() / totalDuration) * totalTargetBars)
          .roundToInt()
          .coerceIn(1, totalTargetBars - 1)
    val newTargetBars = totalTargetBars - oldTargetBars

    // 2. 解码旧柱并弹性缩放到目标分配柱数
    val scaledOldBars = resample(decode(oldBytes), oldTargetBars)

    // 3. 对新片段采样并提取出目标分配柱数
    val newBars = downsample(newRawAmplitudes, newTargetBars)

    // 4. 拼接并统一打包
    return packBars(scaledOldBars + newBars)
  }

  // ====================== 内部底层公用算子 ======================

  /** 从原始高频 FloatArray 降采样为指定数量的柱形值（0.0f ~ 1.0f） */
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
          val v = rawAmplitudes[j]
          if (v > max) max = v
        }
      } else {
        val idx = start.coerceAtMost(rawAmplitudes.lastIndex)
        max = if (idx >= 0) rawAmplitudes[idx] else 0f
      }
      sampled[i] = max.coerceIn(0f, 1f)
    }
    return sampled
  }

  /** 将浮点柱形数组（0.0f ~ 1.0f）量化并紧凑打包为 5-bit ByteArray */
  private fun packBars(bars: FloatArray): ByteArray {
    if (bars.isEmpty()) return ByteArray(1)

    val totalBits = bars.size * 5
    val payloadByteCount = (totalBits + 7) / 8
    val output = ByteArray(1 + payloadByteCount)
    output[0] = bars.size.toByte()

    var bitBuffer = 0
    var bitsInBuffer = 0
    var byteIndex = 1

    for (v in bars) {
      val quantValue = (v * 31).roundToInt().coerceIn(1, 31)
      bitBuffer = (bitBuffer shl 5) or quantValue
      bitsInBuffer += 5

      while (bitsInBuffer >= 8) {
        val shift = bitsInBuffer - 8
        output[byteIndex++] = ((bitBuffer shr shift) and 0xFF).toByte()
        bitsInBuffer -= 8
      }
    }

    if (bitsInBuffer > 0) {
      output[byteIndex] = ((bitBuffer shl (8 - bitsInBuffer)) and 0xFF).toByte()
    }

    return output
  }
}

fun ByteArray.serializeToString(): String = Base64.encode(this)

fun String.deserializeToByteArray(): ByteArray = Base64.decode(this)