package top.fseasy.imlog.features.log.domain

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import top.fseasy.imlog.domain.model.VoiceRecordingState
import java.io.File
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * 封装语音录制全流程，提供响应式状态与计时。
 * [coroutineScope] 用于驱动内部计时器，建议传入 ViewModelScope 保证生命周期一致。
 */
class VoiceRecorder(private val coroutineScope: CoroutineScope) {

    private val _state = MutableStateFlow(VoiceRecordingState.IDLE)
    val state: StateFlow<VoiceRecordingState> = _state.asStateFlow()

    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

    private var mediaRecorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var startTimeMs: Long = 0L
    private var timerJob: Job? = null

    /**
     * 开始录制。允许从 IDLE 或 STOPPED 状态开始。
     */
    @OptIn(ExperimentalUuidApi::class)
    fun start(context: Context) {
        // 允许从 IDLE 或 STOPPED 状态重新开始录音
        if (_state.value == VoiceRecordingState.RECORDING) return

        // 重置状态
        _elapsedMs.value = 0L

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
            Timber.e(e, "Failed to start MediaRecorder")
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
        stopTimer()

        val isStopSuccess = try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            true
        } catch (e: RuntimeException) {
            // 录音时间过短或设备异常时，stop() 可能会抛出异常
            Timber.w(e, "MediaRecorder stop failed (possibly too short recording)")
            false
        } catch (e: Exception) {
            Timber.e(e, "Error releasing MediaRecorder")
            false
        } finally {
            mediaRecorder = null
        }

        val file = currentFile // currentFile had already been handled by the mediaRecoder.
        currentFile = null

        // 如果是取消录音，或者停止时发生异常，则清理文件并返回 null
        if (cancel || !isStopSuccess) {
            file?.delete()
            _state.value = VoiceRecordingState.IDLE
            _elapsedMs.value = 0L
            return null
        } else {
            _state.value = VoiceRecordingState.STOPPED
            return file
        }
    }

    private fun startTimer() {
        stopTimer() // 开启前确保上一次的计时任务已关闭
        timerJob = coroutineScope.launch {
            while (isActive) {
                _elapsedMs.value = System.currentTimeMillis() - startTimeMs
                delay(60) // 配合标准设备刷新率
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
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
            // 配置音频参数以保证音质和跨设备兼容性
            setAudioSamplingRate(44100) // 采样率，CD音质
            setAudioEncodingBitRate(96000) // 比特率，128kbps 是质量和体积的好平衡点
            setOutputFile(outputFile.absolutePath)
        }
    }

    private fun cleanup() {
        stopTimer()
        try {
            mediaRecorder?.release()
        } catch (e: Exception) {
            Timber.e(e, "Error releasing media recorder during cleanup")
        }
        mediaRecorder = null
        currentFile?.delete()
        currentFile = null
        _state.value = VoiceRecordingState.IDLE
        _elapsedMs.value = 0L
    }

    /**
     * 释放资源，可在宿主 (如 ViewModel) 的 onCleared 中调用
     */
    fun release() {
        cleanup()
    }
}