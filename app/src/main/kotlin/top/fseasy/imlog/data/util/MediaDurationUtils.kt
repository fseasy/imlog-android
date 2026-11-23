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
import top.fseasy.imlog.data.commonmain.audio.AdtsFrameParser

object MediaDurationUtils {

  private const val DEFAULT_DURATION_MS = 0L

  // ==========================================
  // File APIs
  // ==========================================

  suspend fun getDuration(file: File): Duration {
    return getDurationOrNull(file) ?: DEFAULT_DURATION_MS.milliseconds
  }

  suspend fun getDurationOrNull(file: File): Duration? =
    withContext(Dispatchers.IO) {
      if (!file.exists() || !file.isFile) return@withContext null

      // 1. Primary: MediaMetadataRetriever
      syncGetDurationFromRetriever { retriever ->
        retriever.setDataSource(file.absolutePath)
      }
      // 2. Secondary Fallback: MediaExtractor
        ?: syncGetDurationFromExtractor { extractor ->
          extractor.setDataSource(file.absolutePath)
        }
        // 3. Final Fallback: 针对无头 ADTS 文件的极速跳帧扫描
        ?: file.inputStream().use { stream ->
          syncGetDurationFromAdtsStream(stream)
        }
    }

  // ==========================================
  // Uri APIs
  // ==========================================

  suspend fun getDuration(context: Context, uri: Uri): Duration {
    return getDurationOrNull(context, uri) ?: DEFAULT_DURATION_MS.milliseconds
  }

  suspend fun getDurationOrNull(context: Context, uri: Uri): Duration? {
    return withContext(Dispatchers.IO) {
      syncGetDurationFromContentResolver(context, uri)
    } ?: getDurationWithReadingFileOrNull(context, uri)
  }

  suspend fun getDurationWithReadingFileOrNull(context: Context, uri: Uri): Duration? =
    withContext(Dispatchers.IO) {
      syncGetDurationFromRetriever { retriever ->
        retriever.setDataSource(context, uri)
      }
        ?: syncGetDurationFromExtractor { extractor ->
          extractor.setDataSource(context, uri, null)
        }
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
            cursor.getString(index)?.toLongOrNull()?.takeIf { it > 0L }?.milliseconds
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
      durationStr?.toLongOrNull()?.takeIf { it > 0L }?.milliseconds
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
          if (durationUs > 0L) {
            return durationUs.microseconds
          }
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

  /**
   * 兜底第三防线：纯字节快速跳帧解析（只读头，不走解码器）
   * 严格确保末尾不完整残帧不计入总时长。
   */
  private fun syncGetDurationFromAdtsStream(inputStream: InputStream): Duration? {
    return try {
      val bufferedStream = inputStream.buffered(8192)
      var totalFrames = 0L
      var totalSamples = 0L
      var baseSampleRate = 0
      val headerBytes = ByteArray(AdtsFrameParser.MIN_HEADER_SIZE)

      while (true) {
        bufferedStream.mark(AdtsFrameParser.MIN_HEADER_SIZE)
        val read = readFully(bufferedStream, headerBytes, AdtsFrameParser.MIN_HEADER_SIZE)
        if (read < AdtsFrameParser.MIN_HEADER_SIZE) break

        val frame = AdtsFrameParser.parseHeader(headerBytes) ?: if (totalFrames == 0L) {
          // 尚未进入有效帧流：向后滑动 1 字节寻找同步字（跳过文件头杂质）
          bufferedStream.reset()
          if (bufferedStream.read() == -1) break
          continue
        } else {
          // 已经在正常帧流中却失去同步：说明到达损坏尾部，立即终止
          break
        }

        val payloadToSkip = (frame.frameLength - AdtsFrameParser.MIN_HEADER_SIZE).toLong()
        if (!skipExact(bufferedStream, payloadToSkip)) {
          // 负载未写满即遇到 EOF（末尾残帧），丢弃该帧
          break
        }

        if (baseSampleRate == 0) {
          baseSampleRate = frame.config.sampleRateHz
        }
        totalFrames++
        totalSamples += frame.rawDataBlocks * 1024L
      }

      if (totalFrames > 0L && baseSampleRate > 0) {
        val durationUs = (totalSamples * 1_000_000L) / baseSampleRate
        durationUs.microseconds
      } else {
        null
      }
    } catch (e: Exception) {
      Timber.w(e, "failed to parse ADTS stream")
      null
    }
  }

  private fun readFully(stream: InputStream, buffer: ByteArray, length: Int): Int {
    var totalRead = 0
    while (totalRead < length) {
      val read = stream.read(buffer, totalRead, length - totalRead)
      if (read == -1) break
      totalRead += read
    }
    return totalRead
  }

  /**
   * 精确跳过 [bytesToSkip] 个字节。
   * 通过读取最后一个字节，规避 FileInputStream.skip() 底层 lseek 越过 EOF 仍返回正数的问题。
   */
  private fun skipExact(stream: InputStream, bytesToSkip: Long): Boolean {
    if (bytesToSkip <= 0L) return true
    var remaining = bytesToSkip
    while (remaining > 1L) {
      val skipped = stream.skip(remaining - 1L)
      if (skipped > 0L) {
        remaining -= skipped
      } else {
        if (stream.read() == -1) return false
        remaining -= 1L
      }
    }
    return stream.read() != -1
  }
}