package top.fseasy.imlog.data.recorder

import android.content.Context
import android.content.Intent
import top.fseasy.imlog.domain.recorder.RecordingGuardian

// androidMain
class AndroidRecordingGuardian(private val context: Context) : RecordingGuardian {
  override fun startGuarding() {
    val intent = Intent(context, VoiceRecordingService::class.java).apply {
      action = VoiceRecordingService.ACTION_GUARD
    }
    context.startForegroundService(intent)
  }

  override fun stopGuarding() {
    val intent = Intent(context, VoiceRecordingService::class.java).apply {
      action = VoiceRecordingService.ACTION_DISMISS
    }
    context.startService(intent)
  }
}
