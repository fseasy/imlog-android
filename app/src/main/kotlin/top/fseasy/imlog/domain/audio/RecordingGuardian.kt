package top.fseasy.imlog.domain.audio

sealed interface RecordingPrompt {
  // 默认标题
  data object Default : RecordingPrompt

  // 资源 ID 类型（支持全自动多语言）
  data class ResBased(val titleResId: Int) : RecordingPrompt

  // 纯文本标题（如“会议：架构组讨论”）
  data class CustomText(val title: String) : RecordingPrompt
}

/**
 * 跨平台的后台保活守护者接口
 * - Android: 启动/停止 ForegroundService + 常驻通知 + WakeLock
 * - iOS: 激活/停用 AVAudioSession 的后台音频模式，或启动 iOS 16+ Live Activity
 */
interface RecordingGuardian {
  fun startGuarding(prompt: RecordingPrompt)

  fun stopGuarding()

  // 默认空实现，方便测试或桌面端/Web端
  object NoOp : RecordingGuardian {
    override fun startGuarding(prompt: RecordingPrompt) {}

    override fun stopGuarding() {}
  }
}
