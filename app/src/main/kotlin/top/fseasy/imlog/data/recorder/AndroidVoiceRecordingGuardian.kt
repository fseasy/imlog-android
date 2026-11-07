package top.fseasy.imlog.data.recorder

import android.content.Context
import android.content.Intent
import timber.log.Timber
import top.fseasy.imlog.domain.recorder.RecordingGuardian
import top.fseasy.imlog.domain.recorder.RecordingPrompt

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

    // 防御 Android 12+ 的 ForegroundServiceStartNotAllowedException 及权限异常
    runCatching {
      context.startForegroundService(intent)
    }
        .onFailure { e ->
          Timber.e(e, "Failed to start VoiceRecordingService as foreground service")
        }
  }

  override fun stopGuarding() {
    val intent =
        Intent(context, VoiceRecordingService::class.java).apply {
          action = VoiceRecordingService.ACTION_DISMISS
        }

    // 防御后台调用 startService 导致的 IllegalStateException
    runCatching {
      context.startService(intent)
    }
        .onFailure { e ->
          // 此处安全：即使因处于后台未能送达 DISMISS 指令，Service 也会在 Flow 监听到 Idle 后自动退出
          Timber.w(
              e,
              "Failed to send ACTION_DISMISS to VoiceRecordingService (app likely in background)",
          )
        }
  }
}
