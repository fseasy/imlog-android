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
import top.fseasy.imlog.domain.model.VoiceRecordingState
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.ExperimentalUuidApi

/**
 * 封装语音录制全流程，提供响应式状态与计时。
 * [coroutineScope] 用于驱动内部计时器，建议传入 ViewModelScope 保证生命周期一致。
 */
class VoiceRecorder(private val coroutineScope: CoroutineScope) : AutoCloseable {

    private val _state = MutableStateFlow(VoiceRecordingState.Idle)
    val state: StateFlow<VoiceRecordingState> = _state.asStateFlow()

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
     * Support AutoClosable.
     * It must be sync function!
     * In most conditions, resource will be released as long as recorder is stopped/canceled.
     * This is just for the edge condition. Sync cleanup should also be fast enough.
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
    suspend fun start(context: Context, outputFile: File) {
        if (_state.value == VoiceRecordingState.Recording) return
        return withContext(Dispatchers.IO) {
            // Reset.
            syncCleanup()
            currentFile = outputFile

            try {
                mediaRecorder = syncCreateMediaRecorder(context, outputFile).apply {
                    prepare()
                    start()
                }
                _state.update { VoiceRecordingState.Recording }
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
        if (_state.value != VoiceRecordingState.Recording) return null
        return withContext(Dispatchers.IO) {
            syncStopRecording()
        }
    }

    /**
     * Cancel recording.
     *
     * Run in IO threads.
     */
    suspend fun cancel() = withContext(Dispatchers.IO) {
        if (_state.value != VoiceRecordingState.Recording) {
            syncCleanup()
            return@withContext
        }
        syncCancelRecording()
    }

    private fun syncCancelRecording() {
        stopTimer()
        syncCleanup()
    }

    /**
     * Will always release mediaRecoder!
     */
    private fun syncStopRecording(): File? {
        stopTimer()
        val isStopSuccess = try {
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
            _state.update { VoiceRecordingState.Stopped }
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
                delay(100.milliseconds)
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
        }.apply {
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
        _state.update { VoiceRecordingState.Idle }
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