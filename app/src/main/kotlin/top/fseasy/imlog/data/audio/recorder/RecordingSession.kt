package top.fseasy.imlog.data.audio.recorder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.core.content.ContextCompat
import java.io.File
import java.io.IOException
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import top.fseasy.imlog.domain.audio.AudioRecordCancelResult
import top.fseasy.imlog.domain.audio.AudioRecordError
import top.fseasy.imlog.domain.audio.AudioRecordException
import top.fseasy.imlog.domain.audio.AudioRecordResult
import top.fseasy.imlog.domain.audio.RecordingOsEvent
import top.fseasy.imlog.domain.model.StoragePathModel

private class OsErrorState
@OptIn(ExperimentalAtomicApi::class)
constructor(
    val maxFileSizeReached: AtomicBoolean = AtomicBoolean(false),
    val nativeErrorOccurred: AtomicBoolean = AtomicBoolean(false),
)

/** 单次录音会话：自闭环管理硬件、采样协程及数据结算 */
internal class RecordingSession
private constructor(
    private val coroutineScope: CoroutineScope,
    private val mediaRecorder: MediaRecorder,
    private val audioFocusHelper: RecordingAudioFocusHelper,
    private val outputPath: ResolvedStoragePath,
    private val autoStopState: OsErrorState,
    private val sampleInterval: Duration,
    private val onSnapshot: (duration: Duration, pulse: Float) -> Unit,
) {
  // Skip add synchronized lock for the amplitudes as
  // 1. only the ticker write it.
  // 2. it will only be read at [stop]. we should join before read it!
  private val collectedAmplitudes = ArrayList<Float>(200)
  private var tickerJob: Job? = null
  private val startMark: TimeMark

  /** hidden the start operations to make it follow use-then-drop rule. */
  init {
    fun startRecording() {
      try {
        mediaRecorder.start()
      } catch (e: Exception) {
        Timber.w(e, "Failed to start MediaRecorder")
        releaseResource()
        throw mapStartException(e)
      }
    }

    suspend fun CoroutineScope.tick() {
      // 1. 读取振幅：若硬件未就绪或发生偶发瞬态抖动，getOrNull 返回 null 或者超时返回 null, 补上 0 来对齐时间
      val rawAmp =
          withTimeoutOrNull(20.milliseconds) {
            runCatching { mediaRecorder.maxAmplitude }.getOrNull()
          } ?: 0
      if (!isActive) return
      val currentDuration = startMark.elapsedNow()

      val pulse = (rawAmp / 32767f).coerceIn(0f, 1f).pow(0.5f)
      collectedAmplitudes.add(pulse)
      onSnapshot(currentDuration, pulse)
    }

    startRecording()
    startMark = TimeSource.Monotonic.markNow()
    tickerJob =
        coroutineScope.launch(Dispatchers.Default) {
          var nextDeadline = startMark.elapsedNow() // 网格原点
          while (isActive) {
            tick()
            nextDeadline += sampleInterval
            // 若 tick 耗时超过 period，跳过已错过的网格点，避免"补偿式"连续执行
            val now = startMark.elapsedNow()
            if (nextDeadline <= now) {
              val missed = ((now - nextDeadline) / sampleInterval).toInt() + 1
              nextDeadline += sampleInterval * missed
            }
            val wait = nextDeadline - startMark.elapsedNow()
            // check before delay
            if (isActive && wait > Duration.ZERO) delay(wait)
          }
        }
  }

  /**
   * No exception should be thrown except that we didn't have the access permission of the file
   * (should be impossible here)
   */
  @OptIn(ExperimentalAtomicApi::class)
  suspend fun stop(): AudioRecordResult {
    tickerJob?.cancel()

    val duration = startMark.elapsedNow()
    val (stopError, fileValid) =
        withContext(Dispatchers.IO) {
          val stopError = stopAndReleaseResource()
          val fileValid = outputPath.file.exists() && outputPath.file.length() > 0L
          Pair(stopError, fileValid)
        }

    // join to wait job done, then we can read the amplitudes safely.
    tickerJob?.join()
    tickerJob = null
    val amplitudes = collectedAmplitudes.toFloatArray()

    /** We use ADTS, so just need to check if file is OK */
    val isTooShort =
        stopError?.error is AudioRecordError.TooShort && duration < MIN_RECORDING_IN_MS.milliseconds
    val isOutputOK = fileValid && !isTooShort
    if (isOutputOK) {
      return AudioRecordResult.Success(outputPath.path, duration, amplitudes)
    }

    val errorReason =
        when {
          autoStopState.maxFileSizeReached.load() -> AudioRecordError.StorageFull
          // MediaRecorder.stop() 在 start() 后极短时间内调用会刻意抛出 RuntimeException
          isTooShort -> AudioRecordError.TooShort

          autoStopState.nativeErrorOccurred.load() -> AudioRecordError.RecorderInternal
          stopError == null -> AudioRecordError.FileIo // && !fileValid
          else -> AudioRecordError.RecorderInternal
        }
    return AudioRecordResult.Failure(outputPath.path, errorReason)
  }

  /** Run in IO thread for necessary parts. No exception will be thrown */
  suspend fun cancel(): AudioRecordCancelResult {
    withContext(Dispatchers.IO) {
      syncRelease()
    }
    return AudioRecordCancelResult(outputPath.path)
  }

  /** No exception */
  fun syncRelease() {
    tickerJob?.cancel()
    tickerJob = null

    stopAndReleaseResource()
  }

  /**
   * 吐掉所有异常，返回 stop() 时抛出的异常
   *
   * @return stop() 抛出的异常；null 表示成功
   */
  private fun stopAndReleaseResource(): AudioRecordException? {
    return try {
      mediaRecorder.stop()
      null
    } catch (e: Throwable) {
      Timber.w(e, "Stop mediaRecorder error")
      mapStopException(e)
    } finally {
      releaseResource()
    }
  }

  /** 吐掉所有异常 */
  private fun releaseResource() {
    runCatching { audioFocusHelper.abandonFocus() }
    runCatching { mediaRecorder.release() }
  }

  companion object {
    /** 判定为 TooShort 的失败时长阈值 */
    private const val MIN_RECORDING_IN_MS = 1000

    /** @throws AudioRecordException */
    suspend fun start(
        context: Context,
        scope: CoroutineScope,
        destinationPath: StoragePathModel.Internal,
        sampleInterval: Duration,
        onSnapshot: (duration: Duration, pulse: Float) -> Unit,
        onOsEvent: (event: RecordingOsEvent) -> Unit,
    ): RecordingSession {

      /**
       * 有几个可能的异常：
       * 1. 协程取消的异常：在 `withContext(Dispatchers.IO)` 切换回来后，可能发现协程已被取消，这时抛出异常
       * 2. RecordingSession start 异常
       * 3. 主动抛出的异常
       *
       * 为了任何一个异常都能将资源释放，定义 [preparedMediaRecorder] 捕获创建的 recorder, 并 try 住整个块并在异常时释放
       */
      var preparedMediaRecorder: MediaRecorder? = null
      val audioFocusHelper = RecordingAudioFocusHelper(context)
      @OptIn(ExperimentalAtomicApi::class) val osErrorState = OsErrorState()

      try {
        val (resolvedPath, recorder) =
            withContext(Dispatchers.IO) {
              val path =
                  try {
                    ResolvedStoragePath.syncCreate(destinationPath, context)
                  } catch (e: SecurityException) {
                    throw AudioRecordException(AudioRecordError.FileIo, "Cannot create dir", e)
                  }
              @OptIn(ExperimentalAtomicApi::class)
              val recorder =
                  syncPrepareMediaRecorder(
                      context.applicationContext,
                      path.file,
                      onStorageFull = {
                        osErrorState.maxFileSizeReached.store(true)
                        onOsEvent(RecordingOsEvent.StorageFull)
                      },
                      onOsNativeError = {
                        osErrorState.nativeErrorOccurred.store(true)
                        onOsEvent(RecordingOsEvent.OsNativeError)
                      },
                  )
              preparedMediaRecorder = recorder
              path to recorder
            }
        val focusGranted =
            audioFocusHelper.requestFocus(recorder) {
              onOsEvent(RecordingOsEvent.FocusLost)
            }
        if (!focusGranted) {
          runCatching { recorder.release() }
          throw AudioRecordException(
              error = AudioRecordError.MicrophoneBusy,
              message = "Failed to get recording focus",
          )
        }
        val session =
            RecordingSession(
                mediaRecorder = recorder,
                audioFocusHelper = audioFocusHelper,
                outputPath = resolvedPath,
                autoStopState = osErrorState,
                sampleInterval = sampleInterval,
                onSnapshot = onSnapshot,
                coroutineScope = scope,
            )
        return session
      } catch (e: Throwable) {
        // release recorder & focus
        runCatching { preparedMediaRecorder?.release() }
        audioFocusHelper.abandonFocus()
        throw e
      }
    }
  }
}

fun syncPrepareMediaRecorder(
    context: Context,
    file: File,
    onStorageFull: () -> Unit,
    onOsNativeError: () -> Unit,
): MediaRecorder {
  if (
      ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
          PackageManager.PERMISSION_DENIED
  ) {
    throw AudioRecordException(
        error = AudioRecordError.PermissionDenied,
        message = "App doesn't have record-audio permission",
    )
  }
  /** 预留空间：防止达到上限前系统或其他应用写入导致磁盘彻底写爆 */
  val storageReserveBytes = 512L * 1024L
  val fileParentDir =
      file.parentFile
          ?: throw AudioRecordException(
              error = AudioRecordError.RecorderInternal,
              message = "output file is invalid: $file",
          )
  val availableBytes = syncGetAvailableBytes(fileParentDir)
  if (availableBytes <= storageReserveBytes) {
    throw AudioRecordException(
        error = AudioRecordError.StorageFull,
        message = "Insufficient storage space: $availableBytes bytes",
    )
  }
  val maxFileSize = availableBytes - storageReserveBytes
  return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        MediaRecorder(context)
      } else {
        @Suppress("DEPRECATION") (MediaRecorder())
      }
      .apply {
        setAudioSource(MediaRecorder.AudioSource.MIC)
        setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
        setAudioEncoder(MediaRecorder.AudioEncoder.AAC) // 务必保持 AAC-LC，见下方说明
        setAudioChannels(1)
        setAudioSamplingRate(24_000)    // 44_100 -> 24_000（或 32_000）
        setAudioEncodingBitRate(40_000) // 64_000 -> 32_000 ~ 48_000 之间按需取舍
        setOutputFile(file.absolutePath)

        setMaxFileSize(maxFileSize)

        setOnInfoListener { _, what, _ ->
          if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
            onStorageFull()
          }
        }

        setOnErrorListener { _, what, extra ->
          Timber.e("MediaRecorder onError: what=$what, extra=$extra")
          onOsNativeError()
        }

        try {
          prepare()
        } catch (e: Throwable) {
          runCatching { release() }
          throw mapStartException(e)
        }
      }
}

private fun mapStartException(e: Throwable): AudioRecordException {
  val error =
      when (e) {
        is SecurityException -> AudioRecordError.PermissionDenied
        is IOException -> AudioRecordError.FileIo
        is IllegalStateException -> AudioRecordError.RecorderInternal
        is RuntimeException -> AudioRecordError.MicrophoneBusy // start() 失败最常见于麦克风被占或无权限
        else -> AudioRecordError.RecorderInternal
      }
  return AudioRecordException(error, e.message, e)
}

private fun mapStopException(e: Throwable): AudioRecordException {
  val error =
      when (e) {
        is IllegalStateException -> AudioRecordError.RecorderInternal
        is RuntimeException -> AudioRecordError.TooShort // system 会刻意抛出
        else -> AudioRecordError.RecorderInternal
      }
  return AudioRecordException(error, e.message, e)
}
