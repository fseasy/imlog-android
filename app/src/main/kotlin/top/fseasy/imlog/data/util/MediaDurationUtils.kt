package top.fseasy.imlog.data.util

import android.content.ContentResolver
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import java.io.File
import java.io.InputStream
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

object MediaDurationUtils {

  private const val DEFAULT_DURATION = 0

  // 标准 ADTS 规范的采样率查找表
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

  // ==========================================
  // File APIs
  // ==========================================

  suspend fun getDuration(file: File): Duration {
    return getDurationOrNull(file) ?: DEFAULT_DURATION.milliseconds
  }

  suspend fun getDurationOrNull(file: File): Duration? =
      withContext(Dispatchers.IO) {
        if (!file.exists() || file.isDirectory) return@withContext null

        // 1. Primary: MediaMetadataRetriever
        syncGetDurationFromRetriever { retriever ->
          retriever.setDataSource(file.absolutePath)
        }
            // 2. Secondary Fallback: MediaExtractor
            ?: syncGetDurationFromExtractor { extractor ->
              extractor.setDataSource(file.absolutePath)
            }
            // 3. Final Fallback: 针对无头 ADTS 文件的极速跳帧扫描（使用 .use 确保绝对安全关闭）
            ?: file.inputStream().use { stream ->
              syncGetDurationFromAdtsStream(stream)
            }
      }

  // ==========================================
  // Uri APIs
  // ==========================================

  suspend fun getDuration(context: Context, uri: Uri): Duration {
    return getDurationOrNull(context, uri) ?: DEFAULT_DURATION.milliseconds
  }

  suspend fun getDurationOrNull(context: Context, uri: Uri): Duration? {
    // Fast Path - Query the ContentResolver database (Zero file parsing, instant)
    return withContext(Dispatchers.IO) {
      syncGetDurationFromContentResolver(context, uri)
    } ?: getDurationWithReadingFileOrNull(context, uri)
  }

  suspend fun getDurationWithReadingFileOrNull(context: Context, uri: Uri): Duration? =
      withContext(Dispatchers.IO) {
        // 1. Standard Path - Use MediaMetadataRetriever
        syncGetDurationFromRetriever { retriever ->
          retriever.setDataSource(context, uri)
        }
            // 2. Deep Path - Use MediaExtractor
            ?: syncGetDurationFromExtractor { extractor ->
              extractor.setDataSource(context, uri, null)
            }
            // 3. Final Fallback: 针对 SAF 流进行纯字节跳帧扫描（使用 .use 杜绝 FD 泄漏）
            ?: context.contentResolver.openInputStream(uri)?.use { stream ->
              syncGetDurationFromAdtsStream(stream)
            }
      }

  // ==========================================
  // Shared Internal Helpers
  // ==========================================

  private fun syncGetDurationFromContentResolver(context: Context, uri: Uri): Duration? {
    if (uri.scheme != ContentResolver.SCHEME_CONTENT) return null

    val projection = arrayOf(MediaStore.MediaColumns.DURATION)
    return try {
      context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
          val index = cursor.getColumnIndex(MediaStore.MediaColumns.DURATION)
          if (index != -1) {
            val durationStr = cursor.getString(index)
            durationStr?.toLongOrNull()?.milliseconds
          } else null
        } else null
      }
    } catch (e: Exception) {
      Timber.w(e, "failed to query media duration from content resolver")
      null
    }
  }

  private fun syncGetDurationFromRetriever(
      setDataSource: (MediaMetadataRetriever) -> Unit
  ): Duration? {
    val retriever = MediaMetadataRetriever()
    return try {
      setDataSource(retriever)
      val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
      durationStr?.toLongOrNull()?.milliseconds
    } catch (e: Exception) {
      Timber.w(e, "failed to get duration from retriever")
      null
    } finally {
      try {
        retriever.release()
      } catch (_: Exception) {}
    }
  }

  private fun syncGetDurationFromExtractor(setDataSource: (MediaExtractor) -> Unit): Duration? {
    val extractor = MediaExtractor()
    return try {
      setDataSource(extractor)
      val trackCount = extractor.trackCount
      for (i in 0 until trackCount) {
        val format = extractor.getTrackFormat(i)
        if (format.containsKey(MediaFormat.KEY_DURATION)) {
          val durationUs = format.getLong(MediaFormat.KEY_DURATION)
          return durationUs.microseconds
        }
      }
      null
    } catch (e: Exception) {
      Timber.w(e, "failed to get duration from extractor")
      null
    } finally {
      try {
        extractor.release()
      } catch (_: Exception) {}
    }
  }

  /** 兜底第三防线：纯字节快速跳帧解析（只读头，不走解码器） 耗时仅数毫秒，专门解决 ADTS 纯裸流无头信息的问题
   * 注意：生命周期由调用方的 .use 统一管理 */
  private fun syncGetDurationFromAdtsStream(inputStream: InputStream): Duration? {
    return try {
      val bufferedStream = inputStream.buffered(8192)
      var totalFrames = 0L
      var sampleRate = 0
      val header = ByteArray(7)

      while (true) {
        bufferedStream.mark(7)
        val read = bufferedStream.read(header, 0, 7)
        if (read < 7) break

        val byte0 = header[0].toInt() and 0xFF
        val byte1 = header[1].toInt() and 0xFF
        // 校验 12-bit 同步字 0xFFF
        if (byte0 != 0xFF || (byte1 and 0xF0) != 0xF0) {
          bufferedStream.reset()
          // 滑动 1 字节寻找同步字（过滤文件头杂质）
          if (bufferedStream.skip(1) <= 0) break
          continue
        }

        // 解析采样率索引 (第 18~21 位)
        val sampleRateIdx = (header[2].toInt() and 0x3C) shr 2
        if (sampleRate == 0 && sampleRateIdx < AAC_SAMPLE_RATES.size) {
          sampleRate = AAC_SAMPLE_RATES[sampleRateIdx]
        }

        // 解析 13-bit 帧长度 (第 30~42 位)
        val byte3 = header[3].toInt() and 0xFF
        val byte4 = header[4].toInt() and 0xFF
        val byte5 = header[5].toInt() and 0xFF
        val frameLength = ((byte3 and 0x03) shl 11) or (byte4 shl 3) or ((byte5 and 0xE0) shr 5)

        if (frameLength < 7) break

        // 跳过音频数据负载，直达下一帧（零 CPU 消耗）
        val payloadToSkip = (frameLength - 7).toLong()
        var skipped = 0L
        while (skipped < payloadToSkip) {
          val s = bufferedStream.skip(payloadToSkip - skipped)
          if (s <= 0) break
          skipped += s
        }

        totalFrames++
      }

      if (totalFrames > 0 && sampleRate > 0) {
        val totalSamples = totalFrames * 1024L // AAC-LC 每帧固定 1024 采样
        val durationUs = (totalSamples * 1_000_000L) / sampleRate
        durationUs.microseconds
      } else {
        null
      }
    } catch (e: Exception) {
      Timber.w(e, "failed to parse ADTS stream")
      null
    }
  }
}
