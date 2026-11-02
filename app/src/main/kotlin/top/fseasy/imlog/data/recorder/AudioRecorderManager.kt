package top.fseasy.imlog.data.recorder

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import top.fseasy.imlog.domain.recorder.AudioRecordResult
import top.fseasy.imlog.domain.recorder.RecordingSnapshot

object AudioRecordingManager {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private var recorder: AndroidAudioRecorder? = null

  // 共享给 UI 观察的状态
  val snapshot: StateFlow<RecordingSnapshot>
    get() = getOrCreateRecorder().snapshot

  fun getOrCreateRecorder(context: Context? = null): AndroidAudioRecorder {
    return recorder ?: synchronized(this) {
      recorder ?: AndroidAudioRecorder(
          context = context?.applicationContext ?: throw IllegalStateException("Context needed for first init"),
          scope = scope
      ).also { recorder = it }
    }
  }

  fun startService(context: Context, filePath: String) {
    val intent = Intent(context, VoiceRecordingService::class.java).apply {
      action = VoiceRecordingService.ACTION_START
      putExtra(VoiceRecordingService.EXTRA_FILE_PATH, filePath)
    }
    context.startForegroundService(intent)
  }

  suspend fun pause() = recorder?.pause()
  suspend fun resume() = recorder?.resume()
  suspend fun stop(): AudioRecordResult? = recorder?.stop()
  suspend fun cancel() = recorder?.cancel()
}
