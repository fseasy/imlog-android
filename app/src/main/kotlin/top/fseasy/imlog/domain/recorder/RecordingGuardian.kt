package top.fseasy.imlog.domain.recorder


/**
 * 跨平台的后台保活守护者接口
 * - Android: 启动/停止 ForegroundService + 常驻通知 + WakeLock
 * - iOS: 激活/停用 AVAudioSession 的后台音频模式，或启动 iOS 16+ Live Activity
 */
interface RecordingGuardian {
  fun startGuarding()
  fun stopGuarding()

  // 默认空实现，方便测试或桌面端/Web端
  object NoOp : RecordingGuardian {
    override fun startGuarding() {}
    override fun stopGuarding() {}
  }
}
