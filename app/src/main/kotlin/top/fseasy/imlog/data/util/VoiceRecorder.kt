package top.fseasy.imlog.data.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
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
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import kotlin.io.path.createParentDirectories
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.ExperimentalUuidApi

enum class VoiceRecorderState {
  Idle,
  Recording,
  Stopped,
}

/**
 * VoiceRecorder that wraps the MediaRecorder & timer
 *
 * @param coroutineScope for internal timer running. Recommended pass the viewModelScope
 */
class VoiceRecorder(private val coroutineScope: CoroutineScope) : AutoCloseable {

  private val _state = MutableStateFlow(VoiceRecorderState.Idle)
  val state: StateFlow<VoiceRecorderState> = _state.asStateFlow()

  private val _elapsedMs = MutableStateFlow(0L)
  val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

  private var mediaRecorder: MediaRecorder? = null
  private var currentFile: File? = null
  private var startTimeMs: Long = 0L
  private var timerJob: Job? = null

  companion object {
    const val OUTPUT_AUDIO_FORMAT = MediaRecorder.OutputFormat.MPEG_4
    const val OUTPUT_AUDIO_ENCODER = MediaRecorder.AudioEncoder.AAC
    const val OUTPUT_AUDIO_MIME_TYPE = "audio/mp4"
    const val OUTPUT_AUDIO_FILE_SUFFIX = ".m4a"

    fun generateOutputAudioDefaultFilename(prefix: String = "recording"): String {
      return "${prefix}_${System.currentTimeMillis()}$OUTPUT_AUDIO_FILE_SUFFIX"
    }
  }

  /**
   * Support AutoClosable. It must be sync function! In most conditions, resource will be released
   * as long as recorder is stopped/canceled. This is just for the edge condition. Sync cleanup
   * should also be fast enough.
   */
  override fun close() = syncCleanup()

  /**
   * Start recording. do nothing if current state is already recording!
   *
   * Run in IO thread.
   *
   * @throws Exception
   */
  @OptIn(ExperimentalUuidApi::class)
  suspend fun start(context: Context, path: java.nio.file.Path) {
    if (_state.value == VoiceRecorderState.Recording) return
    return withContext(Dispatchers.IO) {
      // Reset.
      syncCleanup()
      val currentOutputFile = path.createParentDirectories().toFile()
      currentFile = currentOutputFile

      try {
        mediaRecorder =
            syncCreateMediaRecorder(context, currentOutputFile).apply {
              prepare()
              start()
            }
        _state.update { VoiceRecorderState.Recording }
        startTimeMs = System.currentTimeMillis()
        startTimer()
      } catch (e: Exception) {
        Timber.e(e, "Failed to start MediaRecorder")
        syncCleanup()
        throw e
      }
    }
  }

  /**
   * Stop recording.
   *
   * Run in IO threads.
   *
   * @return recording File if success
   */
  suspend fun stop(): File? {
    if (_state.value != VoiceRecorderState.Recording) return null
    return withContext(Dispatchers.IO) {
      syncStopRecording()
    }
  }

  /**
   * Cancel recording.
   *
   * Run in IO threads.
   */
  suspend fun cancel() =
      withContext(Dispatchers.IO) {
        if (_state.value != VoiceRecorderState.Recording) {
          syncCleanup()
          return@withContext
        }
        syncCancelRecording()
      }

  private fun syncCancelRecording() {
    stopTimer()
    syncCleanup()
  }

  /** Will always release mediaRecoder! */
  private fun syncStopRecording(): File? {
    stopTimer()
    val isStopSuccess =
        try {
          mediaRecorder?.stop()
          true
        } catch (e: Exception) {
          // Can't throw CancellationException as it's in sync block
          Timber.e(e, "MediaRecorder stop failed")
          false
        }

    if (isStopSuccess) {
      // release mediaRecorder and set state to STOP
      syncReleaseMediaRecorder()
      _state.update { VoiceRecorderState.Stopped }
      val resultFile = currentFile
      currentFile = null // reset to null to avoid that clean up delete it.
      return resultFile
    } else {
      // release all resource and reset state
      syncCleanup()
      return null
    }
  }

  private fun startTimer() {
    stopTimer()
    timerJob = coroutineScope.launch {
      while (isActive) {
        _elapsedMs.update { System.currentTimeMillis() - startTimeMs }
        delay(400.milliseconds)
      }
    }
  }

  private fun stopTimer() {
    timerJob?.cancel()
    timerJob = null
  }

  private fun syncCreateMediaRecorder(context: Context, outputFile: File): MediaRecorder {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          MediaRecorder(context)
        } else {
          @Suppress("DEPRECATION") (MediaRecorder())
        }
        .apply {
          setAudioSource(MediaRecorder.AudioSource.MIC)
          setOutputFormat(OUTPUT_AUDIO_FORMAT)
          setAudioEncoder(OUTPUT_AUDIO_ENCODER)
          setAudioSamplingRate(44100)
          setAudioEncodingBitRate(96000)
          setOutputFile(outputFile.absolutePath)
        }
  }

  private fun syncCleanup() {
    stopTimer()
    syncReleaseMediaRecorder()
    try {
      val deleted = currentFile?.delete() ?: true
      if (!deleted) {
        Timber.w("Failed to delete temp recording file: %s", currentFile?.absolutePath)
      }
    } catch (e: SecurityException) {
      Timber.e(e, "Failed to delete temp recording file")
    } finally {
      currentFile = null
    }
    _state.update { VoiceRecorderState.Idle }
    _elapsedMs.value = 0L
  }

  private fun syncReleaseMediaRecorder() {
    try {
      mediaRecorder?.release()
    } catch (e: Exception) {
      Timber.w(e, "MediaRecorder get exception")
      // here can't throw CancellationException as it's sync block
    } finally {
      mediaRecorder = null
    }
  }
}
