package top.fseasy.imlog.data.audio

import android.app.Application
import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import top.fseasy.imlog.data.mapper.toNioPath
import top.fseasy.imlog.domain.audio.AudioRecordCancelResult
import top.fseasy.imlog.domain.audio.AudioRecordResult
import top.fseasy.imlog.domain.audio.AudioRecorder
import top.fseasy.imlog.domain.audio.RecordingSnapshot
import top.fseasy.imlog.domain.model.StoragePathModel
import top.fseasy.imlog.common.runSuspendCatching

class AndroidAudioRecorder(
    private val application: Application,
    private val scope: CoroutineScope,
    private val sampleInterval: Duration = 100.milliseconds,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : AudioRecorder {

  private val _snapshot = MutableStateFlow(RecordingSnapshot())
  override val snapshot: StateFlow<RecordingSnapshot> = _snapshot.asStateFlow()

  private val mutex = Mutex()
  // 所有易变状态均在 session 内部，activeSession != null 即代表正在录音
  private var activeSession: RecordingSession? = null
  private val audioFocusHelper = AudioFocusHelper(application)

  /** Run IO operations in IO thread. */
  override suspend fun start(
      destinationPath: StoragePathModel.Internal,
      onRecordingFocusLost: (() -> Unit)?,
  ): Result<Unit> = runSuspendCatching {
    val focusGranted =
        audioFocusHelper.requestFocus(
            onFocusLost = {
              // 录音中途突然来电：通知外层
              onRecordingFocusLost?.invoke()
            }
        )

    if (!focusGranted) {
      // 提示用户“麦克风或音频被占用，无法录音”，直接返回，省去创建 MediaRecorder 的开销
      throw IllegalStateException("Can't get AudioRecording focus")
    }
    mutex.withLock {
      if (activeSession != null)
          throw IllegalStateException("Invalid state: start with active session")
      // update status immediately
      _snapshot.value = RecordingSnapshot(status = RecordingSnapshot.Status.Recording)
      // then start recording session
      activeSession =
          RecordingSession.start(
              context = application,
              scope = scope,
              destinationPath = destinationPath,
              sampleInterval = sampleInterval,
              timeSource = timeSource,
              onSnapshot = { duration, pulse ->
                _snapshot.update { s ->
                  if (s.status == RecordingSnapshot.Status.Recording) {
                    s.copy(duration = duration, currentPulse = pulse)
                  } else {
                    s
                  }
                }
              },
          )
    }
  }
      .onFailure {
        Timber.e(it, "Failed to start AudioRecorder")
        _snapshot.value = RecordingSnapshot(status = RecordingSnapshot.Status.Idle)
      }

  override suspend fun stop(): Result<AudioRecordResult> = runSuspendCatching {
    mutex.withLock {
      val session =
          activeSession
              ?: throw IllegalStateException("Invalid State: call stop while activeSession = null")
      activeSession = null // 移出会话引用，防止重入

      val result =
          try {
            session.stop()
          } finally {
            audioFocusHelper.abandonFocus()
            _snapshot.update { s ->
              s.copy(
                  status = RecordingSnapshot.Status.Idle,
                  currentPulse = 0f, // 录音结束，振幅归零
              )
            }
          }
      result
    }
  }

  /** Run in IO thread for necessary parts. */
  override suspend fun cancel(): Result<AudioRecordCancelResult> = runSuspendCatching {
    mutex.withLock {
      val session =
          activeSession
              ?: throw IllegalStateException("Invalid State: call Cancel while no active session")
      activeSession = null
      val result =
          try {
            session.cancel()
          } finally {
            audioFocusHelper.abandonFocus()
            _snapshot.value = RecordingSnapshot(status = RecordingSnapshot.Status.Idle)
          }
      result
    }
  }

  override fun close() {
    _snapshot.value = RecordingSnapshot(status = RecordingSnapshot.Status.Idle)
    val currentSession = activeSession ?: return
    activeSession = null
    currentSession.syncRelease()
    audioFocusHelper.abandonFocus()
  }

  override val outputAudioFileExtension: String = ".aac"
  override val outputAudioMimeType: String = "audio/aac"
}

/** 单次录音会话：自闭环管理硬件、采样协程及数据结算 */
private class RecordingSession
private constructor(
    private val outputPath: ResolvedStoragePath,
    private val mediaRecorder: MediaRecorder,
    private val startMark: TimeMark,
    private val sampleInterval: Duration,
    private val scope: CoroutineScope,
    private val onSnapshot: (duration: Duration, pulse: Float) -> Unit,
) {
  private val collectedWaveform = ArrayList<Float>(500)
  /** 只所以用锁而非 join 来互斥读写，是因为非常极端的情况下，JNI 读取可能卡死，这样 Join 会连带卡死我们的程序。 */
  private val collectedWaveformLock = Any()
  private var tickerJob: Job? = null

  private fun startTicker() {
    tickerJob =
        scope.launch(Dispatchers.Default) {
          while (isActive) {
            val currentDuration = startMark.elapsedNow()

            // 1. 读取振幅：若硬件未就绪或发生偶发瞬态抖动，getOrNull 返回 null, 补上 0 来对齐时间
            val rawAmp = runCatching { mediaRecorder.maxAmplitude }.getOrNull() ?: 0

            // 前面是一个长耗时的 JNI 操作；可能在这个时间内，协程收到 cancel 信号，所以再做一个快速检查
            if (!isActive) break

            val pulse = (rawAmp / 32767f).coerceIn(0f, 1f).pow(0.5f)
            // Synchronize to avoid race-condition when cancel/stop
            synchronized(collectedWaveformLock) {
              collectedWaveform.add(pulse)
            }
            onSnapshot(currentDuration, pulse)
            delay(sampleInterval)
          }
        }
  }

  /**
   * No exception should be thrown except that we didn't have the access permission of the file
   * (should be impossible here)
   */
  suspend fun stop(): AudioRecordResult {
    // 1. 立即发出采样取消信号, 再等 recorder 关闭后再去拷贝数据，减少 race-condition 和录音脏数据
    tickerJob?.cancel()
    tickerJob = null

    // 2. 硬件停止、释放与文件检查，全部收拢进 IO 线程（确保 Main-Thread Safe）
    val duration = startMark.elapsedNow()
    val isSuccess =
        withContext(Dispatchers.IO) {
          val stopOk = stopAndReleaseMediaRecorder()
          stopOk && outputPath.file.exists() && outputPath.file.length() > 0
        }

    // 3. 现在同步读波形——不用管 tickerJob 是否实际已结束
    val finalWaveform =
        synchronized(collectedWaveformLock) {
          collectedWaveform.toFloatArray()
        }

    // 4. 安全结算
    return if (isSuccess) {
      AudioRecordResult.Success(outputPath.path, duration, finalWaveform)
    } else {
      AudioRecordResult.Failure(outputPath.path)
    }
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

    stopAndReleaseMediaRecorder()
  }

  /** swallow all exceptions */
  private fun stopAndReleaseMediaRecorder(): Boolean {
    return try {
      mediaRecorder.stop()
      true
    } catch (e: Throwable) {
      Timber.w(e, "Stop mediaRecorder error")
      false
    } finally {
      runCatching { mediaRecorder.release() }
    }
  }

  companion object {
    /**
     * @throws IllegalStateException from MediaRecorder
     * @throws SecurityException from file (parent dirs) creating
     */
    suspend fun start(
        context: Context,
        scope: CoroutineScope,
        destinationPath: StoragePathModel.Internal,
        sampleInterval: Duration,
        timeSource: TimeSource,
        onSnapshot: (duration: Duration, pulse: Float) -> Unit,
    ): RecordingSession {

      val (resolvedPath, recorder) =
          withContext(Dispatchers.IO) {
            val path = ResolvedStoragePath.syncCreate(destinationPath, context)
            val recorder =
                syncCreateMediaRecorder(context, path.file).apply {
                  try {
                    prepare()
                    start()
                  } catch (e: Throwable) {
                    runCatching { release() }
                    throw e
                  }
                }
            path to recorder
          }

      return RecordingSession(
              outputPath = resolvedPath,
              mediaRecorder = recorder,
              startMark = timeSource.markNow(),
              sampleInterval = sampleInterval,
              scope = scope,
              onSnapshot = onSnapshot,
          )
          .apply { startTicker() }
    }

    /** @throws IllegalStateException */
    private fun syncCreateMediaRecorder(context: Context, file: File): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
              MediaRecorder(context)
            } else {
              @Suppress("DEPRECATION") MediaRecorder()
            }
            .apply {
              setAudioSource(MediaRecorder.AudioSource.MIC)
              setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
              setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
              setAudioChannels(1)
              setAudioSamplingRate(44_100)
              setAudioEncodingBitRate(64_000)
              setOutputFile(file.absolutePath)
            }
  }
}

private data class ResolvedStoragePath(
    val path: StoragePathModel.Internal,
    val file: File,
) {
  companion object {
    /** @throws SecurityException */
    fun syncCreate(path: StoragePathModel.Internal, context: Context): ResolvedStoragePath {
      val file = path.toNioPath(context).toFile().apply { parentFile?.mkdirs() }
      return ResolvedStoragePath(path = path, file = file)
    }
  }
}
