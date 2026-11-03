package top.fseasy.imlog.data.recorder

import android.app.Application
import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import kotlin.math.pow
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import top.fseasy.imlog.domain.recorder.AudioRecordResult
import top.fseasy.imlog.domain.recorder.AudioRecorder
import top.fseasy.imlog.domain.recorder.RecordingSnapshot

class AndroidAudioRecorder(
    context: Context,
    private val scope: CoroutineScope,
    private val sampleInterval: Duration = 100.milliseconds,
) : AudioRecorder {

  // 1. 使用 Application 类型，彻底消灭 StaticFieldLeak Warning
  private val application: Application = context.applicationContext as Application

  private val _snapshot = MutableStateFlow(RecordingSnapshot())
  override val snapshot: StateFlow<RecordingSnapshot> = _snapshot.asStateFlow()

  private val mutex = Mutex()
  private var mediaRecorder: MediaRecorder? = null
  private var activeFile: File? = null

  private val timeSource = TimeSource.Monotonic
  private var startMark: ComparableTimeMark? = null
  private var tickerJob: Job? = null

  // 仅在 samplingJob 的单线程中访问，或者在 cancelAndJoin 停止后安全访问
  private val collectedWaveform = ArrayList<Float>(500)

  override suspend fun start(destinationPath: String): Result<Unit> = runCatching {
    mutex.withLock {
      if (_snapshot.value.status == RecordingSnapshot.Status.Recording) return@runCatching

      // 开启前全面重置（防上一次异常残留）
      internalReset(deleteFile = true)

      val file = File(destinationPath).apply { parentFile?.mkdirs() }
      activeFile = file

      mediaRecorder =
          createMediaRecorder(file).apply {
            prepare()
            start()
          }

      startMark = timeSource.markNow()
      _snapshot.value = RecordingSnapshot(status = RecordingSnapshot.Status.Recording)

      startSampling()
    }
  }
      .onFailure { Timber.e(it, "Failed to start AudioRecorder") }

  // 彻底删除了 pause() 和 resume()！

  override suspend fun stop(): AudioRecordResult? = mutex.withLock {
    if (_snapshot.value.status != RecordingSnapshot.Status.Recording) return null

    // 核心优化 1：使用 cancelAndJoin 确保采样完全停止，消灭并发竞态
    stopSamplingSafely()

    val file = activeFile
    val duration = startMark?.elapsedNow() ?: Duration.ZERO

    // 核心 Bug 修复：导出不可变列表，杜绝引用被清空
    val finalWaveform = collectedWaveform.toList()

    val isStopSuccess = runCatching {
      mediaRecorder?.stop()
      true
    }
        .getOrElse { false }

    releaseMediaRecorder()

    // 核心优化 2：无论成功与否，状态彻底归零
    return if (isStopSuccess && file != null && file.exists() && file.length() > 0) {
      internalReset(deleteFile = false) // 成功：保留物理文件，复位内存
      _snapshot.value = RecordingSnapshot(status = RecordingSnapshot.Status.Stopped)
      AudioRecordResult(file.absolutePath, duration, finalWaveform)
    } else {
      internalReset(deleteFile = true) // 失败：清理无效垃圾文件
      null
    }
  }

  override suspend fun cancel() = mutex.withLock {
    stopSamplingSafely()
    internalReset(deleteFile = true)
  }

  override fun close() {
    tickerJob?.cancel()
    tickerJob = null
    releaseMediaRecorder()
    activeFile?.delete()
    activeFile = null
    collectedWaveform.clear()
  }

  private fun startSampling() {
    tickerJob =
        scope.launch(Dispatchers.Default) {
          while (isActive) {
            val currentDuration = startMark?.elapsedNow() ?: Duration.ZERO
            val rawAmp = runCatching { mediaRecorder?.maxAmplitude ?: 0 }.getOrDefault(0)
            val pulse = (rawAmp / 32767f).coerceIn(0f, 1f).pow(0.5f)

            collectedWaveform.add(pulse)

            _snapshot.value =
                RecordingSnapshot(
                    status = RecordingSnapshot.Status.Recording,
                    duration = currentDuration,
                    currentPulse = pulse,
                )
            delay(sampleInterval)
          }
        }
  }

  /** 挂起直到采样协程完全退出，确保对 MediaRecorder 和 collectedWaveform 访问的安全 */
  private suspend fun stopSamplingSafely() {
    tickerJob?.cancelAndJoin()
    tickerJob = null
  }

  private fun releaseMediaRecorder() {
    runCatching { mediaRecorder?.release() }
    mediaRecorder = null
  }

  /** 单一真实来源的重置函数 */
  private fun internalReset(deleteFile: Boolean) {
    releaseMediaRecorder()
    if (deleteFile) {
      activeFile?.delete()
    }
    activeFile = null
    startMark = null
    collectedWaveform.clear()
    _snapshot.value = RecordingSnapshot(status = RecordingSnapshot.Status.Idle)
  }

  private fun createMediaRecorder(file: File): MediaRecorder =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(application)
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

  /** 音频文件后缀（包含点号，如 ".m4a" 或 ".aac"） */
  override val fileExtension: String
    get() = ".aac"

  /** 音频 MIME 类型，如 "audio/mp4" 或 "audio/aac" */
  override val mimeType: String
    get() = "audio/aac"
}
