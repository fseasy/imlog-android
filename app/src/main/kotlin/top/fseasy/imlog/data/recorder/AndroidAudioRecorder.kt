package top.fseasy.imlog.data.recorder

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import top.fseasy.imlog.domain.recorder.AudioRecordResult
import top.fseasy.imlog.domain.recorder.AudioRecorder
import top.fseasy.imlog.domain.recorder.RecordingSnapshot
import java.io.File
import kotlin.math.pow
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

class AndroidAudioRecorder(
  private val context: Context,
  private val scope: CoroutineScope,
  private val sampleInterval: Duration = 100.milliseconds,
) : AudioRecorder {

  private val _snapshot = MutableStateFlow(RecordingSnapshot())
  override val snapshot: StateFlow<RecordingSnapshot> = _snapshot.asStateFlow()

  private val mutex = Mutex()
  private var mediaRecorder: MediaRecorder? = null
  private var activeFile: File? = null

  private val timeSource = TimeSource.Monotonic
  private var accumulatedDuration: Duration = Duration.ZERO
  private var segmentStartMark: ComparableTimeMark? = null
  private var tickerJob: Job? = null

  override suspend fun start(destinationPath: String): Result<Unit> = runCatching {
    mutex.withLock {
      if (_snapshot.value.status == RecordingSnapshot.Status.Recording) return@runCatching
      cleanup(deleteFile = true)

      val file = File(destinationPath).apply { parentFile?.mkdirs() }
      activeFile = file

      // 核心调整：采用 AAC_ADTS 流式编码，杜绝断电/强杀导致的文件损坏
      mediaRecorder = createMediaRecorder(context, file).apply {
        prepare()
        start()
      }

      accumulatedDuration = Duration.ZERO
      segmentStartMark = timeSource.markNow()
      _snapshot.value = RecordingSnapshot(status = RecordingSnapshot.Status.Recording)

      startSampling()
    }
  }.onFailure { Timber.e(it, "Failed to start AudioRecorder") }

  override suspend fun pause(): Result<Unit> = runCatching {
    mutex.withLock {
      if (_snapshot.value.status != RecordingSnapshot.Status.Recording) return@runCatching
      mediaRecorder?.pause()
      stopSampling()
      segmentStartMark?.let { accumulatedDuration += it.elapsedNow() }
      segmentStartMark = null
      _snapshot.update {
        it.copy(
            status = RecordingSnapshot.Status.Paused,
            duration = accumulatedDuration,
            currentPulse = 0f
        )
      }
    }
  }

  override suspend fun resume(): Result<Unit> = runCatching {
    mutex.withLock {
      if (_snapshot.value.status != RecordingSnapshot.Status.Paused) return@runCatching
      mediaRecorder?.resume()
      segmentStartMark = timeSource.markNow()
      _snapshot.update { it.copy(status = RecordingSnapshot.Status.Recording) }
      startSampling()
    }
  }

  override suspend fun stop(): AudioRecordResult? = mutex.withLock {
    val status = _snapshot.value.status
    if (status != RecordingSnapshot.Status.Recording && status != RecordingSnapshot.Status.Paused) {
      return null
    }

    stopSampling()
    if (status == RecordingSnapshot.Status.Recording) {
      segmentStartMark?.let { accumulatedDuration += it.elapsedNow() }
    }

    val resultFile = activeFile
    val resultDuration = accumulatedDuration
    val resultWaveform = _snapshot.value.waveform

    val isStopSuccess = runCatching {
      mediaRecorder?.stop()
      true
    }.getOrElse { false }

    releaseMediaRecorder()

    // 即使 stop() 失败（如时间过短），只要有流式数据写入，文件依然保留部分
    if (resultFile != null && resultFile.exists() && resultFile.length() > 0) {
      activeFile = null
      _snapshot.update { it.copy(status = RecordingSnapshot.Status.Stopped, currentPulse = 0f) }
      AudioRecordResult(resultFile.absolutePath, resultDuration, resultWaveform)
    } else {
      cleanup(deleteFile = true)
      null
    }
  }

  override suspend fun cancel() = mutex.withLock { cleanup(deleteFile = true) }

  override fun close() {
    stopSampling()
    releaseMediaRecorder()
    activeFile?.delete()
    activeFile = null
  }

  private fun startSampling() {
    stopSampling()
    tickerJob = scope.launch(Dispatchers.Default) {
      while (isActive) {
        val currentDuration = accumulatedDuration + (segmentStartMark?.elapsedNow() ?: Duration.ZERO)
        val rawAmp = runCatching { mediaRecorder?.maxAmplitude ?: 0 }.getOrDefault(0)
        val pulse = (rawAmp / 32767f).coerceIn(0f, 1f).pow(0.5f)

        _snapshot.update {
          it.copy(
              duration = currentDuration,
              currentPulse = pulse,
              waveform = it.waveform + pulse
          )
        }
        delay(sampleInterval)
      }
    }
  }

  private fun stopSampling() {
    tickerJob?.cancel()
    tickerJob = null
  }

  private fun releaseMediaRecorder() {
    runCatching { mediaRecorder?.release() }
    mediaRecorder = null
  }

  private fun cleanup(deleteFile: Boolean) {
    stopSampling()
    releaseMediaRecorder()
    if (deleteFile) activeFile?.delete()
    activeFile = null
    accumulatedDuration = Duration.ZERO
    segmentStartMark = null
    _snapshot.value = RecordingSnapshot()
  }

  private fun createMediaRecorder(context: Context, file: File): MediaRecorder {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      MediaRecorder(context)
    } else {
      @Suppress("DEPRECATION") MediaRecorder()
    }.apply {
      setAudioSource(MediaRecorder.AudioSource.MIC)
      // 关键：选用 AAC_ADTS 格式，抗异常杀进程
      setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
      setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
      setAudioSamplingRate(44100)
      setAudioEncodingBitRate(96000)
      setOutputFile(file.absolutePath)
    }
  }
}
