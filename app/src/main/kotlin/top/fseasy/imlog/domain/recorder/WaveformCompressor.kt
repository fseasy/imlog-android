package top.fseasy.imlog.domain.recorder

import kotlin.math.roundToInt

object WaveformCodec5Bit {

  /** 1. 动态决定存储柱数（30 ~ 100） 规则：基础 30 根 + 每录制 1 秒增加 1 根，100 根封顶 */
  fun calculateTargetBars(durationSeconds: Long): Int {
    return (30 + durationSeconds).toInt().coerceIn(30, 100)
  }

  /**
   * 2. 压缩：原始 FloatArray -> 5-bit Bit-Packed ByteArray 数据结构：[第0字节: 柱子总数量
   *    N] + [后续字节: N * 5 bits 的紧凑位流]
   */
  fun encode(rawAmplitudes: FloatArray, targetBars: Int): ByteArray {
    if (rawAmplitudes.isEmpty()) {
      return ByteArray(1) { 0 }
    }

    // 步骤 A: 降采样 + 量化到 1..31
    val sampled = IntArray(targetBars)
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

      sampled[i] = (max * 31).roundToInt().coerceIn(1, 31)
    }

    // 步骤 B: 高效 5-bit 位打包（bit buffer）
    val totalBits = targetBars * 5
    val payloadByteCount = (totalBits + 7) / 8
    val output = ByteArray(1 + payloadByteCount)
    output[0] = targetBars.toByte()

    var bitBuffer = 0
    var bitsInBuffer = 0
    var byteIndex = 1

    for (value in sampled) {
      // MSB first，与原版保持一致
      bitBuffer = (bitBuffer shl 5) or value
      bitsInBuffer += 5

      while (bitsInBuffer >= 8) {
        val shift = bitsInBuffer - 8
        output[byteIndex++] = ((bitBuffer shr shift) and 0xFF).toByte()
        bitsInBuffer -= 8
      }
    }

    // 处理末尾残留 bits
    if (bitsInBuffer > 0) {
      output[byteIndex] = ((bitBuffer shl (8 - bitsInBuffer)) and 0xFF).toByte()
    }

    return output
  }

  /** 兼容旧接口：List<Float> 入口 */
  fun encode(rawAmplitudes: List<Float>, targetBars: Int): ByteArray {
    return encode(rawAmplitudes.toFloatArray(), targetBars)
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
            // 数据不完整，按 0 处理
            bitsInBuffer = 0
            0
          }

      result[i] = (value / 31f).coerceIn(0.05f, 1.0f)
    }

    return result
  }

  /** 兼容旧接口：返回 List<Float> */
  fun decodeToList(bytes: ByteArray?): List<Float> {
    return decode(bytes).asList()
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

  /** 兼容旧接口 */
  fun resample(sourceBars: List<Float>, targetBarCount: Int): List<Float> {
    return resample(sourceBars.toFloatArray(), targetBarCount).asList()
  }
}
