package top.fseasy.imlog.data.audio

import android.content.Context
import android.content.Intent
import timber.log.Timber
import top.fseasy.imlog.domain.audio.RecordingGuardian
import top.fseasy.imlog.domain.audio.RecordingPrompt

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
      // 不需要担心它阻塞主线程
      // AMS 处理这次登记全是内存操作，正常耗时在微秒到亚毫秒级，和 startActivity()、bindService()、checkSelfPermission()
      // 属于同一个量级，Android 官方和 StrictMode 都认可直接在主线程调用。
      // 它不是磁盘或网络 IO，所以依然不需要用 withContext(Dispatchers.IO) 去包装它。只有当系统本身发生极其严重的全局锁卡死（system_server
      // 卡顿）时它才会慢，但那种情况下全系统所有 Binder 调用都会一起卡，切线程也避不开。
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
