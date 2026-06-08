package top.fseasy.imlog.features.log.domain

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.fseasy.imlog.domain.model.VoiceRecordingState
import java.io.File
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * 封装语音录制全流程，提供响应式状态与计时。
 */
class VoiceRecorder {

    private val _state = MutableStateFlow(VoiceRecordingState.IDLE)
    val state: StateFlow<VoiceRecordingState> = _state.asStateFlow()

    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

    private var mediaRecorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var startTimeMs: Long = 0L
    private var timerJob: Job? = null

    /**
     * 开始录制，[context] 用于创建 MediaRecorder（Android S 以上需要）。
     * 调用前必须处于 IDLE 状态。
     */
    @OptIn(ExperimentalUuidApi::class)
    fun start(context: Context) {
        if (_state.value != VoiceRecordingState.IDLE) return
        val voiceTmpName = "voice_${
            Uuid.random()
                .toHexString()
        }.m4a"
        val file = File(context.cacheDir, voiceTmpName)
        currentFile = file

        try {
            mediaRecorder = createMediaRecorder(context, file).apply {
                prepare()
                start()
            }
            _state.value = VoiceRecordingState.RECORDING
            startTimeMs = System.currentTimeMillis()
            startTimer()
        } catch (e: Exception) {
            e.printStackTrace()
            cleanup()
        }
    }

    /**
     * 停止录制并返回音频文件；若录制尚未开始或已取消，返回 null。
     */
    fun stop(): File? {
        if (_state.value != VoiceRecordingState.RECORDING) return null
        return finishRecording(cancel = false)
    }

    /**
     * 取消录制，删除临时文件，返回 null。
     */
    fun cancel() {
        if (_state.value == VoiceRecordingState.IDLE) return
        finishRecording(cancel = true)
    }

    private fun finishRecording(cancel: Boolean): File? {
        timerJob?.cancel()
        timerJob = null

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaRecorder = null
        }

        val file = currentFile
        currentFile = null

        if (cancel) {
            file?.delete()
            _state.value = VoiceRecordingState.IDLE
            _elapsedMs.value = 0L
            return null
        } else {
            _state.value = VoiceRecordingState.STOPPED
            // 保留最终时长，供发送时使用
            return file
        }
    }

    private fun startTimer() {
        timerJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                _elapsedMs.value = System.currentTimeMillis() - startTimeMs
                delay(50) // 更新频率足够流畅
            }
        }
    }

    private fun createMediaRecorder(context: Context, outputFile: File): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(outputFile.absolutePath)
        }
    }

    private fun cleanup() {
        mediaRecorder?.release()
        mediaRecorder = null
        currentFile?.delete()
        currentFile = null
        _state.value = VoiceRecordingState.IDLE
        _elapsedMs.value = 0L
    }
}
