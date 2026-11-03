package top.fseasy.imlog.data.recorder

import android.content.Context
import android.content.Intent
import top.fseasy.imlog.domain.recorder.RecordingGuardian
import top.fseasy.imlog.domain.recorder.RecordingPrompt

// androidMain
class AndroidRecordingGuardian(private val context: Context) : RecordingGuardian {
  override fun startGuarding(prompt: RecordingPrompt) {
    val intent =
        Intent(context, VoiceRecordingService::class.java).apply {
          action = VoiceRecordingService.ACTION_GUARD
          when (prompt) {
            is RecordingPrompt.ResBased ->
                putExtra(VoiceRecordingService.EXTRA_TITLE_RES_ID, prompt.titleResId)
            is RecordingPrompt.CustomText ->
                putExtra(VoiceRecordingService.EXTRA_CUSTOM_TITLE, prompt.title)
            RecordingPrompt.Default -> {}
          }
        }
    context.startForegroundService(intent)
  }

  override fun stopGuarding() {
    val intent =
        Intent(context, VoiceRecordingService::class.java).apply {
          action = VoiceRecordingService.ACTION_DISMISS
        }
    context.startService(intent)
  }
}
