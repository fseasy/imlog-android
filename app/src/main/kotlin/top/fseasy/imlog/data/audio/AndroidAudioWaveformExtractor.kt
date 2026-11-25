package top.fseasy.imlog.data.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber
import top.fseasy.imlog.data.commonmain.audio.WaveformStreamingAccumulator
import top.fseasy.imlog.data.mapper.toUriOrThrow
import top.fseasy.imlog.domain.audio.AudioWaveformExtractor
import top.fseasy.imlog.domain.audio.AudioWaveformResult
import top.fseasy.imlog.domain.model.StoragePathModel
import top.fseasy.imlog.domain.repository.StorageRepository

class AndroidAudioWaveformExtractor(
  private val context: Context,
  private val storageRepository: StorageRepository,
) : AudioWaveformExtractor {

  /**
   * Safe for main-thread.
   *
   * @throws CancellationException, any other exceptions will lead to null result.
   */
  override suspend fun extractOrNull(
    path: StoragePathModel,
    knownDuration: Duration?,
  ): AudioWaveformResult? =
    withContext(Dispatchers.IO) {
      if (path is StoragePathModel.Dual) {
        return@withContext extractOrNull(path.toInternal(), knownDuration)
      }

      // 在创建 MediaExtractor / MediaCodec 前获取许可，避免列表并发时打满编解码器实例或 FD
      codecSemaphore.withPermit {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null

        try {
          when (path) {
            is StoragePathModel.Internal -> {
              val internalPath =
                storageRepository.resolveInternalStoragePathToAbsolutePathWithoutCreating(path)
              extractor.setDataSource(internalPath.value.value)
            }
            is StoragePathModel.Shared -> {
              val sharedPath =
                storageRepository.resolveSharedStoragePathToAbsolutePathWithoutCreating(path)
              extractor.setDataSource(context, sharedPath.value.toUriOrThrow(), null)
            }
          }

          var trackIndex = -1
          var format: MediaFormat? = null
          var mimeType = ""

          for (i in 0 until extractor.trackCount) {
            val mf = extractor.getTrackFormat(i)
            val mime = mf.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
              trackIndex = i
              format = mf
              mimeType = mime
              break
            }
          }

          if (trackIndex < 0 || format == null) return@withPermit null
          extractor.selectTrack(trackIndex)

          val resolvedDuration =
            knownDuration?.takeIf { it > Duration.ZERO && it.isFinite() }
              ?: if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION).takeIf { it > 0L }?.microseconds
              } else {
                null
              }

          val accumulator = WaveformStreamingAccumulator(resolvedDuration)

          val activeCodec = MediaCodec.createDecoderByType(mimeType)
          codec = activeCodec
          activeCodec.configure(format, null, null, 0)
          activeCodec.start()

          val session =
            DecoderSession(
              extractor = extractor,
              codec = activeCodec,
              accumulator = accumulator,
              initialFormat = format,
            )

          // 短音频走全量顺序解码（零 Seek 开销、峰值 100% 精确）；长音频走等距 Seek 抽样
          val strategy: DecodeStrategy =
            if (resolvedDuration != null && resolvedDuration > LONG_AUDIO_THRESHOLD) {
              SparseSeekDecodeStrategy(resolvedDuration.inWholeMicroseconds)
            } else {
              SequentialDecodeStrategy
            }

          val isSparseSampled = strategy.decode(session)
          accumulator.finish(preferEstimatedDuration = isSparseSampled)
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Timber.w(e, "Failed to extract waveform for $path")
          null
        } finally {
          runCatching { codec?.stop() }
          runCatching { codec?.release() }
          runCatching { extractor.release() }
        }
      }
    }

  /** 封装编解码器缓冲区状态与多格式 PCM 读取细节，供不同解码策略复用。 */
  private class DecoderSession(
    val extractor: MediaExtractor,
    val codec: MediaCodec,
    private val accumulator: WaveformStreamingAccumulator,
    initialFormat: MediaFormat,
  ) {
    private val bufferInfo = MediaCodec.BufferInfo()
    private val shortBuffer = ShortArray(4096)

    private var channelCount =
      initialFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT, 1).coerceAtLeast(1)
    private var sampleRate =
      initialFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE, 44100).coerceAtLeast(8000)
    private var pcmEncoding =
      readPcmEncoding(runCatching { codec.outputFormat }.getOrNull(), initialFormat)
    private var alignedBatchSize = (shortBuffer.size / channelCount) * channelCount

    fun tryFeedInput(): FeedResult {
      val inIndex = codec.dequeueInputBuffer(0)
      if (inIndex < 0) return FeedResult.BUSY

      val inputBuffer = codec.getInputBuffer(inIndex)
      if (inputBuffer == null) {
        codec.queueInputBuffer(inIndex, 0, 0, 0, 0)
        return FeedResult.FED_PACKET
      }

      val sampleSize = extractor.readSampleData(inputBuffer, 0)
      return if (sampleSize < 0) {
        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        FeedResult.FED_EOS
      } else {
        codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
        extractor.advance()
        FeedResult.FED_PACKET
      }
    }

    fun tryDrainOutput(timeoutUs: Long): DrainResult {
      return when (val outIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)) {
        MediaCodec.INFO_TRY_AGAIN_LATER -> DrainResult.TRY_AGAIN
        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
          val newFormat = codec.outputFormat
          channelCount =
            newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT, channelCount).coerceAtLeast(1)
          sampleRate =
            newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE, sampleRate).coerceAtLeast(8000)
          pcmEncoding = readPcmEncoding(newFormat)
          alignedBatchSize = (shortBuffer.size / channelCount) * channelCount
          DrainResult.FORMAT_CHANGED
        }
        MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> DrainResult.BUFFERS_CHANGED
        else -> {
          if (outIndex < 0) return DrainResult.TRY_AGAIN

          val outputBuffer = codec.getOutputBuffer(outIndex)
          val pcmBytes = bufferInfo.size
          var hasPcm = false

          if (outputBuffer != null && pcmBytes > 0) {
            hasPcm = true
            dispatchPcmBuffer(outputBuffer, pcmBytes, bufferInfo.presentationTimeUs)
          }

          codec.releaseOutputBuffer(outIndex, false)
          val isEos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
          DrainResult.DECODED(hasPcm = hasPcm, isEos = isEos)
        }
      }
    }

    private fun readPcmEncoding(primary: MediaFormat?, fallback: MediaFormat? = null): Int {
      if (primary != null && primary.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
        return primary.getInteger(MediaFormat.KEY_PCM_ENCODING)
      }
      if (fallback != null && fallback.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
        return fallback.getInteger(MediaFormat.KEY_PCM_ENCODING)
      }
      return AudioFormat.ENCODING_PCM_16BIT
    }

    private fun dispatchPcmBuffer(outputBuffer: ByteBuffer, pcmBytes: Int, pts: Long) {
      outputBuffer.position(bufferInfo.offset)
      outputBuffer.limit(bufferInfo.offset + pcmBytes)
      outputBuffer.order(ByteOrder.LITTLE_ENDIAN)

      when {
        pcmEncoding == AudioFormat.ENCODING_PCM_16BIT -> {
          // 99% 场景的快路径：原生批量拷贝
          val shortView = outputBuffer.asShortBuffer()
          val totalShorts = shortView.remaining()
          var readOffset = 0
          while (readOffset < totalShorts) {
            val count = minOf(alignedBatchSize, totalShorts - readOffset)
            shortView.get(shortBuffer, 0, count)
            feedChunk(count, readOffset, pts)
            readOffset += count
          }
        }
        pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT -> {
          val floatView = outputBuffer.asFloatBuffer()
          val totalSamples = floatView.remaining()
          var readOffset = 0
          while (readOffset < totalSamples) {
            val count = minOf(alignedBatchSize, totalSamples - readOffset)
            for (i in 0 until count) {
              shortBuffer[i] = (floatView.get().coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            }
            feedChunk(count, readOffset, pts)
            readOffset += count
          }
        }
        pcmEncoding == AudioFormat.ENCODING_PCM_8BIT -> {
          val totalSamples = outputBuffer.remaining()
          var readOffset = 0
          while (readOffset < totalSamples) {
            val count = minOf(alignedBatchSize, totalSamples - readOffset)
            for (i in 0 until count) {
              val centered = (outputBuffer.get().toInt() and 0xFF) - 128
              shortBuffer[i] = (centered shl 8).toShort()
            }
            feedChunk(count, readOffset, pts)
            readOffset += count
          }
        }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            pcmEncoding == AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
          val totalSamples = outputBuffer.remaining() / 3
          var readOffset = 0
          while (readOffset < totalSamples) {
            val count = minOf(alignedBatchSize, totalSamples - readOffset)
            for (i in 0 until count) {
              outputBuffer.get() // 丢弃小端最低字节
              val mid = outputBuffer.get().toInt() and 0xFF
              val high = outputBuffer.get().toInt() // 保留最高字节符号位
              shortBuffer[i] = ((high shl 8) or mid).toShort()
            }
            feedChunk(count, readOffset, pts)
            readOffset += count
          }
        }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            pcmEncoding == AudioFormat.ENCODING_PCM_32BIT -> {
          val intView = outputBuffer.asIntBuffer()
          val totalSamples = intView.remaining()
          var readOffset = 0
          while (readOffset < totalSamples) {
            val count = minOf(alignedBatchSize, totalSamples - readOffset)
            for (i in 0 until count) {
              shortBuffer[i] = (intView.get() shr 16).toShort()
            }
            feedChunk(count, readOffset, pts)
            readOffset += count
          }
        }
      }
    }

    private fun feedChunk(chunkShortCount: Int, readOffset: Int, pts: Long) {
      val frameOffset = readOffset / channelCount
      val chunkPtsUs = pts + (frameOffset * (1_000_000.0 / sampleRate)).toLong()
      accumulator.accumulate(
        pcmShorts = shortBuffer,
        shortCount = chunkShortCount,
        channelCount = channelCount,
        sampleRate = sampleRate,
        presentationTimeUs = chunkPtsUs,
      )
    }

    enum class FeedResult {
      BUSY,
      FED_PACKET,
      FED_EOS,
    }

    sealed interface DrainResult {
      data object TRY_AGAIN : DrainResult

      data object FORMAT_CHANGED : DrainResult

      data object BUFFERS_CHANGED : DrainResult

      data class DECODED(val hasPcm: Boolean, val isEos: Boolean) : DrainResult
    }
  }

  private sealed interface DecodeStrategy {
    /** @return true 表示使用了稀疏抽样，false 表示进行了全量顺序解码。 */
    suspend fun decode(session: DecoderSession): Boolean
  }

  /** 策略 A：短音频全量顺序解码（0 超时连续排空）。 */
  private data object SequentialDecodeStrategy : DecodeStrategy {
    private const val MAX_CONSECUTIVE_TIMEOUTS = 200

    override suspend fun decode(session: DecoderSession): Boolean {
      var isInputEOS = false
      var isOutputEOS = false
      var consecutiveTimeouts = 0

      while (!isOutputEOS) {
        coroutineContext.ensureActive()
        var didWork = false

        while (!isInputEOS) {
          when (session.tryFeedInput()) {
            DecoderSession.FeedResult.BUSY -> break
            DecoderSession.FeedResult.FED_PACKET -> didWork = true
            DecoderSession.FeedResult.FED_EOS -> {
              didWork = true
              isInputEOS = true
            }
          }
        }

        while (!isOutputEOS) {
          val timeoutUs = if (didWork) 0L else 5000L
          when (val result = session.tryDrainOutput(timeoutUs)) {
            DecoderSession.DrainResult.TRY_AGAIN -> {
              if (isInputEOS && timeoutUs > 0L && ++consecutiveTimeouts >= MAX_CONSECUTIVE_TIMEOUTS) {
                isOutputEOS = true
              }
              break
            }
            DecoderSession.DrainResult.FORMAT_CHANGED,
            DecoderSession.DrainResult.BUFFERS_CHANGED -> {
              didWork = true
            }
            is DecoderSession.DrainResult.DECODED -> {
              didWork = true
              consecutiveTimeouts = 0
              if (result.isEos) isOutputEOS = true
            }
          }
        }
      }
      return false
    }
  }

  /**
   * 策略 B：长音频等距 Seek 抽样解码。
   * 仅针对每根柱子的中心位置抽样解码 1~2 帧 PCM；若发现容器不支持 Seek，会自动回退到全量顺序解码。
   */
  private class SparseSeekDecodeStrategy(
    private val durationUs: Long,
  ) : DecodeStrategy {

    override suspend fun decode(session: DecoderSession): Boolean {
      var hasFedCodec = false
      var lastSeekSampleTime = -1L

      for (i in 0 until SAMPLE_POINTS) {
        currentCoroutineContext().ensureActive()

        val targetUs = (((i + 0.5) / SAMPLE_POINTS) * durationUs).toLong()
        session.extractor.seekTo(targetUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

        val sampleTime = session.extractor.sampleTime
        if (sampleTime < 0L) continue

        // 容器不支持 Seek 时位置会死锁在开头附近，自动回退到全量顺序解码
        if (targetUs > durationUs / 5 && sampleTime <= 1_000_000L) {
          session.extractor.seekTo(0L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
          if (hasFedCodec) session.codec.flush()
          return SequentialDecodeStrategy.decode(session)
        }

        if (sampleTime == lastSeekSampleTime) continue
        lastSeekSampleTime = sampleTime

        if (hasFedCodec) {
          session.codec.flush()
        }

        hasFedCodec = true
        decodeWindowAtCurrentPosition(session)
      }

      return true
    }

    private suspend fun decodeWindowAtCurrentPosition(session: DecoderSession) {
      var fedPackets = 0
      var decodedPcmFrames = 0
      var isInputEOS = false
      var isOutputEOS = false
      var emptyPolls = 0

      while (!isOutputEOS &&
        decodedPcmFrames < TARGET_PCM_FRAMES_PER_POINT &&
        emptyPolls < MAX_EMPTY_POLLS_PER_POINT) {
        currentCoroutineContext().ensureActive()
        var didWork = false

        while (!isInputEOS && fedPackets < MAX_INPUT_PACKETS_PER_POINT) {
          when (session.tryFeedInput()) {
            DecoderSession.FeedResult.BUSY -> break
            DecoderSession.FeedResult.FED_PACKET -> {
              didWork = true
              fedPackets++
            }
            DecoderSession.FeedResult.FED_EOS -> {
              didWork = true
              isInputEOS = true
            }
          }
        }

        while (!isOutputEOS && decodedPcmFrames < TARGET_PCM_FRAMES_PER_POINT) {
          val timeoutUs = if (didWork) 0L else 2000L
          when (val result = session.tryDrainOutput(timeoutUs)) {
            DecoderSession.DrainResult.TRY_AGAIN -> {
              if (timeoutUs > 0L) emptyPolls++
              break
            }
            DecoderSession.DrainResult.FORMAT_CHANGED,
            DecoderSession.DrainResult.BUFFERS_CHANGED -> {
              didWork = true
            }
            is DecoderSession.DrainResult.DECODED -> {
              didWork = true
              emptyPolls = 0
              if (result.hasPcm) decodedPcmFrames++
              if (result.isEos) isOutputEOS = true
            }
          }
        }
      }
    }

    private companion object {
      const val SAMPLE_POINTS = 100
      const val TARGET_PCM_FRAMES_PER_POINT = 2
      const val MAX_INPUT_PACKETS_PER_POINT = 8
      const val MAX_EMPTY_POLLS_PER_POINT = 15
    }
  }

  private companion object {
    val LONG_AUDIO_THRESHOLD = 90.seconds
    val codecSemaphore = Semaphore(permits = 2)
  }
}