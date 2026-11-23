package top.fseasy.imlog.data.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
      knownDuration: kotlin.time.Duration?,
  ): AudioWaveformResult? =
      withContext(Dispatchers.IO) {
        if (path is StoragePathModel.Dual) {
          return@withContext extractOrNull(path.toInternal(), knownDuration)
        }
        // 实例化 commonMain 的累加器
        val accumulator = WaveformStreamingAccumulator(knownDuration)

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

          if (trackIndex < 0 || format == null) return@withContext null
          extractor.selectTrack(trackIndex)

          val activeCodec = MediaCodec.createDecoderByType(mimeType)
          codec = activeCodec
          activeCodec.configure(format, null, null, 0)
          activeCodec.start()

          var channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT, 1).coerceAtLeast(1)
          var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE, 44100).coerceAtLeast(8000)

          val bufferInfo = MediaCodec.BufferInfo()
          var isInputEOS = false
          var isOutputEOS = false
          var consecutiveTimeouts = 0
          val maxConsecutiveTimeouts = 200

          val shortBuffer = ShortArray(4096) // 复用的小缓冲数组
          // channelCount 在 INFO_OUTPUT_FORMAT_CHANGED 会更新，需要对应重新计算
          var alignedBatchSize = (shortBuffer.size / channelCount) * channelCount

          while (!isOutputEOS) {
            currentCoroutineContext().ensureActive()

            // 1. 数据入队
            if (!isInputEOS) {
              val inIndex = activeCodec.dequeueInputBuffer(5000)
              if (inIndex >= 0) {
                val inputBuffer = activeCodec.getInputBuffer(inIndex)
                if (inputBuffer != null) {
                  val sampleSize = extractor.readSampleData(inputBuffer, 0)
                  if (sampleSize < 0) {
                    activeCodec.queueInputBuffer(
                        inIndex,
                        0,
                        0,
                        0,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                    )
                    isInputEOS = true
                  } else {
                    activeCodec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                    extractor.advance()
                  }
                }
              }
            }

            // 2. PCM 出队
            val outIndex = activeCodec.dequeueOutputBuffer(bufferInfo, 5000)
            when (outIndex) {
              MediaCodec.INFO_TRY_AGAIN_LATER -> {
                if (isInputEOS && ++consecutiveTimeouts >= maxConsecutiveTimeouts) break
              }
              MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                val newFormat = activeCodec.outputFormat
                channelCount =
                    newFormat
                        .getInteger(MediaFormat.KEY_CHANNEL_COUNT, channelCount)
                        .coerceAtLeast(1)
                sampleRate =
                    newFormat
                        .getInteger(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
                        .coerceAtLeast(8000)
                alignedBatchSize = (shortBuffer.size / channelCount) * channelCount
              }
              MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
              else -> {
                if (outIndex >= 0) {
                  consecutiveTimeouts = 0
                  val outputBuffer = activeCodec.getOutputBuffer(outIndex)
                  val pcmBytes = bufferInfo.size

                  if (outputBuffer != null && pcmBytes > 0) {
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.limit(bufferInfo.offset + pcmBytes)
                    outputBuffer.order(ByteOrder.LITTLE_ENDIAN)

                    val shortView = outputBuffer.asShortBuffer()
                    val totalShorts = shortView.remaining()
                    var readOffset = 0
                    val pts = bufferInfo.presentationTimeUs

                    // 流式喂给 accumulator
                    while (readOffset < totalShorts) {
                      val chunkShortCount = minOf(alignedBatchSize, totalShorts - readOffset)

                      shortView.get(shortBuffer, 0, chunkShortCount)

                      // 计算当前批次的起始微秒偏移
                      val frameOffset = readOffset / channelCount
                      val chunkPtsUs = pts + (frameOffset * (1_000_000.0 / sampleRate)).toLong()

                      // 【重点】：直接调用 common 累加器！
                      accumulator.accumulate(
                          pcmShorts = shortBuffer,
                          shortCount = chunkShortCount,
                          channelCount = channelCount,
                          sampleRate = sampleRate,
                          presentationTimeUs = chunkPtsUs,
                      )
                      readOffset += chunkShortCount
                    }
                  }

                  activeCodec.releaseOutputBuffer(outIndex, false)
                  if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    isOutputEOS = true
                  }
                }
              }
            }
          }

          // 委托 accumulator 打包并输出结果
          accumulator.finish()
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
