/** TODO: migrates the java based io to KMP io. */
package top.fseasy.imlog.data.commonmain.audio

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

sealed interface AdtsQuickCheckResult {
  data class Valid(val config: AdtsAudioConfig) : AdtsQuickCheckResult

  data object Missing : AdtsQuickCheckResult

  data class Invalid(val reason: String) : AdtsQuickCheckResult
}

sealed interface AdtsPendingScanResult {
  data class Valid(
      val config: AdtsAudioConfig,
      val frameCount: Long,
      val duration: Duration,
      val validByteLength: Long,
      val trimmedTrailingBytes: Long,
  ) : AdtsPendingScanResult

  data object Missing : AdtsPendingScanResult

  data class Corrupted(
      val reason: String,
      val fileSize: Long,
  ) : AdtsPendingScanResult
}

object AdtsDraftScanner {

  /** O(1) 快速校验（用于已定稿的 base 或 Committed 草稿）： 只检查文件存在性及前 1~2 帧头合法性，避免进聊天页时全文件扫描或触发 Retriever IPC。 */
  suspend fun quickValidate(file: File): AdtsQuickCheckResult =
      withContext(Dispatchers.IO) {
        if (!file.exists() || !file.isFile) {
          return@withContext AdtsQuickCheckResult.Missing
        }
        val fileLength = file.length()
        if (fileLength < AdtsFrameParser.MIN_HEADER_SIZE) {
          return@withContext AdtsQuickCheckResult.Invalid(
              "File too small: $fileLength bytes",
          )
        }

        try {
          RandomAccessFile(file, "r").use { raf ->
            val header = ByteArray(AdtsFrameParser.MIN_HEADER_SIZE)
            raf.readFully(header)

            val firstFrame =
                AdtsFrameParser.parseHeader(header)
                    ?: return@withContext AdtsQuickCheckResult.Invalid(
                        "Invalid ADTS header at offset 0",
                    )

            if (firstFrame.frameLength.toLong() > fileLength) {
              return@withContext AdtsQuickCheckResult.Invalid(
                  "First frame truncated: expected ${firstFrame.frameLength}, actual $fileLength",
              )
            }

            // 如果文件足够长，顺便做第 2 帧双帧对齐校验，几乎彻底杜绝随机字节误判
            val secondOffset = firstFrame.frameLength.toLong()
            if (secondOffset + AdtsFrameParser.MIN_HEADER_SIZE <= fileLength) {
              raf.seek(secondOffset)
              raf.readFully(header)
              val secondFrame = AdtsFrameParser.parseHeader(header)
              if (secondFrame == null || secondFrame.config != firstFrame.config) {
                return@withContext AdtsQuickCheckResult.Invalid(
                    "Second ADTS frame misaligned or config mismatch at offset $secondOffset",
                )
              }
            }

            AdtsQuickCheckResult.Valid(config = firstFrame.config)
          }
        } catch (e: Exception) {
          Timber.w(e, "quickValidate failed for ${file.name}")
          AdtsQuickCheckResult.Invalid(e.message ?: "Read failed")
        }
      }

  /**
   * 针对录音中断遗留的 pendingSegment：
   * 1. 全帧边界扫描，精确统计有效帧数与时长；
   * 2. 遇到末尾截断残帧时，若前面存在有效帧且 [trimCorruptedTail] 为 true，直接截断保留完整帧；
   * 3. 要求整个文件音频参数 (profile/sampleRate/channel) 一致，防止局部乱码污染。
   */
  suspend fun scanAndRepairPending(
      file: File,
      trimCorruptedTail: Boolean = true,
  ): AdtsPendingScanResult =
      withContext(Dispatchers.IO) {
        if (!file.exists() || !file.isFile) {
          return@withContext AdtsPendingScanResult.Missing
        }
        val fileLength = file.length()
        if (fileLength < AdtsFrameParser.MIN_HEADER_SIZE) {
          return@withContext AdtsPendingScanResult.Corrupted(
              reason = "File too small ($fileLength bytes)",
              fileSize = fileLength,
          )
        }

        try {
          var firstConfig: AdtsAudioConfig? = null
          var frameCount = 0L
          var totalSamples = 0L
          var offset = 0L

          RandomAccessFile(file, "r").use { raf ->
            val header = ByteArray(AdtsFrameParser.MIN_HEADER_SIZE)

            while (offset + AdtsFrameParser.MIN_HEADER_SIZE <= fileLength) {
              raf.seek(offset)
              raf.readFully(header)

              val frame = AdtsFrameParser.parseHeader(header) ?: break
              val frameLen = frame.frameLength.toLong()

              // 当前帧未写满（强杀导致的半帧截断），停止于上一帧边界
              if (offset + frameLen > fileLength) break

              // 校验采样率/声道等核心配置一致
              val baseConfig = firstConfig
              if (baseConfig == null) {
                firstConfig = frame.config
              } else if (frame.config != baseConfig) {
                break
              }

              frameCount++
              totalSamples += frame.rawDataBlocks * 1024L
              offset += frameLen
            }
          }

          val resolvedConfig = firstConfig
          if (frameCount == 0L || resolvedConfig == null) {
            return@withContext AdtsPendingScanResult.Corrupted(
                reason = "No valid ADTS frames found",
                fileSize = fileLength,
            )
          }

          val validByteLength = offset
          val trimmedTrailingBytes = fileLength - validByteLength

          if (trimmedTrailingBytes > 0L && trimCorruptedTail) {
            RandomAccessFile(file, "rw").use { raf ->
              raf.setLength(validByteLength)
            }
            Timber.i(
                "Trimmed $trimmedTrailingBytes trailing bytes from ADTS draft ${file.name}, kept $frameCount frames",
            )
          }

          val durationUs = (totalSamples * 1_000_000L) / resolvedConfig.sampleRateHz
          AdtsPendingScanResult.Valid(
              config = resolvedConfig,
              frameCount = frameCount,
              duration = durationUs.microseconds,
              validByteLength = validByteLength,
              trimmedTrailingBytes = trimmedTrailingBytes,
          )
        } catch (e: Exception) {
          Timber.w(e, "scanAndRepairPending failed for ${file.name}")
          AdtsPendingScanResult.Corrupted(
              reason = e.message ?: "Scan failed",
              fileSize = fileLength,
          )
        }
      }

  /**
   * 将 [baseFile] 与 [pendingFile] 的有效部分按顺序合并写入 [destTempFile]。 调用前应确保两者通过了校验且 AdtsAudioConfig 一致。
   */
  suspend fun mergeAdtsFiles(
      baseFile: File,
      pendingFile: File,
      pendingValidByteLength: Long,
      destTempFile: File,
  ): Result<Unit> =
      withContext(Dispatchers.IO) {
        runCatching {
          require(pendingValidByteLength > 0L) { "pendingValidByteLength must be > 0" }
          destTempFile.parentFile?.mkdirs()

          FileOutputStream(destTempFile).use { out ->
            FileInputStream(baseFile).use { baseIn ->
              baseIn.copyTo(out)
            }
            FileInputStream(pendingFile).use { pendingIn ->
              AdtsDraftScanner.copyExactBytes(
                  pendingIn,
                  out,
                  pendingValidByteLength,
              )
            }
            out.fd.sync()
          }
        }
      }

  private fun copyExactBytes(
      input: FileInputStream,
      output: FileOutputStream,
      byteCount: Long,
  ) {
    val buffer = ByteArray(8192)
    var remaining = byteCount
    while (remaining > 0L) {
      val toRead = minOf(buffer.size.toLong(), remaining).toInt()
      val read = input.read(buffer, 0, toRead)
      if (read == -1) {
        throw IllegalStateException("Unexpected EOF while merging ADTS, remaining=$remaining")
      }
      output.write(buffer, 0, read)
      remaining -= read
    }
  }
}
