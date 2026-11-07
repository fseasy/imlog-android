package top.fseasy.imlog.data.recorder

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
import top.fseasy.imlog.domain.model.StoragePathModel
import top.fseasy.imlog.domain.recorder.AudioRecordResult
import top.fseasy.imlog.domain.recorder.AudioRecorder
import top.fseasy.imlog.domain.recorder.RecordingSnapshot
import top.fseasy.imlog.domain.util.runSuspendCatching

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

  override suspend fun start(destinationPath: StoragePathModel.Internal): Result<Unit> =
      runSuspendCatching {
        mutex.withLock {
          if (activeSession != null) return@runSuspendCatching
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

  override suspend fun stop(): AudioRecordResult? = mutex.withLock {
    val session = activeSession ?: return null
    activeSession = null // 移出会话引用，防止重入

    val result = session.stop()
    _snapshot.update { s ->
      s.copy(
          status = RecordingSnapshot.Status.Idle,
          currentPulse = 0f, // 录音结束，振幅归零
      )
    }
    result
  }

  override suspend fun cancel(): AudioRecordResult.Failure? = mutex.withLock {
    val session = activeSession ?: return null
    activeSession = null
    val result = session.cancel()
    _snapshot.value = RecordingSnapshot(status = RecordingSnapshot.Status.Idle)
    result
  }

  override fun close() {
    activeSession?.syncRelease()
    activeSession = null
    _snapshot.value = RecordingSnapshot(status = RecordingSnapshot.Status.Idle)
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
  private var tickerJob: Job? = null

  private fun startTicker() {
    tickerJob =
        scope.launch(Dispatchers.Default) {
          while (isActive) {
            val currentDuration = startMark.elapsedNow()

            // 1. 读取振幅：若硬件未就绪或发生偶发瞬态抖动，getOrNull 返回 null
            val rawAmp = runCatching { mediaRecorder.maxAmplitude }.getOrNull()

            // 前面是一个长耗时的 JNI 操作；可能在这个时间内，协程收到 cancel 信号，所以再做一个快速检查
            if (!isActive) break

            if (rawAmp == null) {
              // 保持 UI 计时器正常运转，但不向波形列表塞脏数据
              onSnapshot(currentDuration, 0f)
              delay(sampleInterval)
              continue
            }

            val pulse = (rawAmp / 32767f).coerceIn(0f, 1f).pow(0.5f)
            collectedWaveform.add(pulse)
            onSnapshot(currentDuration, pulse)
            delay(sampleInterval)
          }
        }
  }

  suspend fun stop(): AudioRecordResult {
    // 1. 立即发出采样取消信号
    tickerJob?.cancel()

    // 2. 硬件停止、释放与文件检查，全部收拢进 IO 线程（确保 Main-Thread Safe）
    val duration = startMark.elapsedNow()
    val isSuccess =
        withContext(Dispatchers.IO) {
          val stopOk = stopAndReleaseMediaRecorder()
          stopOk && outputPath.file.exists() && outputPath.file.length() > 0
        }

    // 3. 在读取波形前等待 ticker 彻底退出
    tickerJob?.join()
    tickerJob = null

    // 4. 安全结算
    return if (isSuccess) {
      AudioRecordResult.Success(outputPath.path, duration, collectedWaveform)
    } else {
      AudioRecordResult.Failure(outputPath.path)
    }
  }

  suspend fun cancel(): AudioRecordResult.Failure {
    withContext(Dispatchers.IO) {
      syncRelease()
    }
    return AudioRecordResult.Failure(outputPath.path)
  }

  fun syncRelease() {
    // 不关心波形结果，直接取消采样协程，无需 join 挂起等待
    tickerJob?.cancel()
    tickerJob = null

    stopAndReleaseMediaRecorder()
  }

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
              setAudioSamplingRate(44100)
              setAudioEncodingBitRate(96000)
              setOutputFile(file.absolutePath)
            }
  }
}

private data class ResolvedStoragePath(
    val path: StoragePathModel.Internal,
    val file: File,
) {
  companion object {
    fun syncCreate(path: StoragePathModel.Internal, context: Context): ResolvedStoragePath {
      val file = path.toNioPath(context).toFile().apply { parentFile?.mkdirs() }
      return ResolvedStoragePath(path = path, file = file)
    }
  }
}
