package top.fseasy.imlog.domain.recorder

sealed interface RecordingSessionState {
  // 空闲
  data object Idle : RecordingSessionState

  // 模式 1：按住录音中（轻量态，无通知栏，松手即发，左滑即毁）
  data object Holding : RecordingSessionState

  // 模式 2：锁定/点按长录音态（重量态，挂前台服务，可暂停、试听、存草稿）
  data class Locked(val isPaused: Boolean) : RecordingSessionState
}