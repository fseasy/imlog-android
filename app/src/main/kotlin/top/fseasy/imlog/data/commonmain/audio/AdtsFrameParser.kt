package top.fseasy.imlog.data.commonmain.audio

data class AdtsAudioConfig(
    val profile: Int,
    val sampleRateIndex: Int,
    val sampleRateHz: Int,
    val channelConfig: Int,
)

internal data class AdtsFrameHeader(
    val config: AdtsAudioConfig,
    val headerSize: Int,
    val frameLength: Int,
    val rawDataBlocks: Int,
)

internal object AdtsFrameParser {
  const val MIN_HEADER_SIZE = 7

  // 标准 ADTS 规范的采样率查找表 (index 0..12，13..15 为保留/非法)
  private val AAC_SAMPLE_RATES =
      intArrayOf(
          96000,
          88200,
          64000,
          48000,
          44100,
          32000,
          24000,
          22050,
          16000,
          12000,
          11025,
          8000,
          7350,
      )

  fun parseHeader(header: ByteArray): AdtsFrameHeader? {
    if (header.size < MIN_HEADER_SIZE) return null

    val byte0 = header[0].toInt() and 0xFF
    val byte1 = header[1].toInt() and 0xFF
    // 1. 校验 12-bit 同步字 0xFFF，且 ADTS layer (bits 13..14) 必须为 00
    if (byte0 != 0xFF || (byte1 and 0xF6) != 0xF0) return null

    // protection_absent == 1 -> 无 CRC (7 字节头); 0 -> 有 2 字节 CRC (9 字节头)
    val protectionAbsent = (byte1 and 0x01) == 1
    val headerSize = if (protectionAbsent) 7 else 9

    val byte2 = header[2].toInt() and 0xFF
    // 2. 校验采样率索引 (bits 18..21)
    val sampleRateIdx = (byte2 and 0x3C) shr 2
    if (sampleRateIdx !in AAC_SAMPLE_RATES.indices) return null
    val sampleRateHz = AAC_SAMPLE_RATES[sampleRateIdx]

    val profile = (byte2 and 0xC0) shr 6

    val byte3 = header[3].toInt() and 0xFF
    val channelConfig = ((byte2 and 0x01) shl 2) or ((byte3 and 0xC0) shr 6)

    // 3. 解析 13-bit 帧总长度 (含头 + 负载)
    val byte4 = header[4].toInt() and 0xFF
    val byte5 = header[5].toInt() and 0xFF
    val frameLength = ((byte3 and 0x03) shl 11) or (byte4 shl 3) or ((byte5 and 0xE0) shr 5)
    if (frameLength < headerSize) return null

    // 4. 每个 ADTS 帧包含的 AAC 原始数据块数量 (通常为 1)
    val byte6 = header[6].toInt() and 0xFF
    val rawDataBlocks = (byte6 and 0x03) + 1

    return AdtsFrameHeader(
        config =
            AdtsAudioConfig(
                profile = profile,
                sampleRateIndex = sampleRateIdx,
                sampleRateHz = sampleRateHz,
                channelConfig = channelConfig,
            ),
        headerSize = headerSize,
        frameLength = frameLength,
        rawDataBlocks = rawDataBlocks,
    )
  }
}
