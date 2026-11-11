// domain/audio/WaveformCodec5Bit.kt 或 data/codec/WaveformCodec5Bit.kt
package top.fseasy.imlog.domain.audio

import kotlin.math.roundToInt

object WaveformCodec5Bit {

  /** 仅在写库/序列化时调用：Waveform -> 5-bit ByteArray */
  fun pack(waveform: Waveform): ByteArray {
    val bars = waveform.bars
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

  /** 仅在读库/反序列化时调用：5-bit ByteArray -> Waveform */
  fun unpack(bytes: ByteArray?): Waveform {
    if (bytes == null || bytes.isEmpty()) return Waveform.EMPTY

    val count = bytes[0].toInt() and 0xFF
    if (count == 0) return Waveform.EMPTY

    val result = FloatArray(count)
    var byteIndex = 1
    var bitBuffer = 0
    var bitsInBuffer = 0

    for (i in 0 until count) {
      while (bitsInBuffer < 5 && byteIndex < bytes.size) {
        bitBuffer = (bitBuffer shl 8) or (bytes[byteIndex].toInt() and 0xFF)
        bitsInBuffer += 8
        byteIndex++
      }

      val value = if (bitsInBuffer >= 5) {
        val v = (bitBuffer shr (bitsInBuffer - 5)) and 0x1F
        bitsInBuffer -= 5
        v
      } else {
        bitsInBuffer = 0
        0
      }
      result[i] = (value / 31f).coerceIn(0.05f, 1.0f)
    }

    return Waveform(result)
  }
}