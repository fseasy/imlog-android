package top.fseasy.imlog.data.audio

import android.content.Context
import android.content.Intent
import java.util.concurrent.atomic.AtomicBoolean
import timber.log.Timber
import top.fseasy.imlog.domain.audio.RecordingGuardian
import top.fseasy.imlog.domain.audio.RecordingPrompt

class AndroidRecordingGuardian(context: Context) : RecordingGuardian {

  private val appContext = context.applicationContext
  private val isGuarding = AtomicBoolean(false)

  override fun startGuarding(prompt: RecordingPrompt) {
    val intent =
      Intent(appContext, VoiceRecordingService::class.java).apply {
        action = VoiceRecordingService.ACTION_GUARD
        when (prompt) {
          is RecordingPrompt.ResBased ->
            putExtra(VoiceRecordingService.EXTRA_TITLE_RES_ID, prompt.titleResId)
          is RecordingPrompt.CustomText ->
            putExtra(VoiceRecordingService.EXTRA_CUSTOM_TITLE, prompt.title)
          RecordingPrompt.Default -> Unit
        }
      }

    // 防御 Android 12+ 的 ForegroundServiceStartNotAllowedException 及权限异常
    runCatching {
      // AMS 处理这次登记全是内存操作，正常耗时在微秒到亚毫秒级，官方认可直接在主线程调用
      appContext.startForegroundService(intent)
    }
      .onSuccess {
        isGuarding.set(true)
      }
      .onFailure { e ->
        Timber.e(e, "Failed to start VoiceRecordingService as foreground service")
      }
  }

  /** 幂等且低开销：若当前未处于守护状态（或已经停止过），直接 O(1) 返回，不会拉起空 Service */
  override fun stopGuarding() {
    if (!isGuarding.compareAndSet(true, false)) return

    // 仍采用 ACTION_DISMISS 而非 stopService()，避免紧邻 startGuarding() 调用时触发
    // ForegroundServiceDidNotStartInTimeException
    val intent =
      Intent(appContext, VoiceRecordingService::class.java).apply {
        action = VoiceRecordingService.ACTION_DISMISS
      }

    runCatching {
      appContext.startService(intent)
    }
      .onFailure { e ->
        // 若此时处于后台导致 startService 失败，Service 内部会通过 Flow 监听及看门狗自动退出
        Timber.w(
          e,
          "Failed to send ACTION_DISMISS to VoiceRecordingService (app likely in background)",
        )
      }
  }
}