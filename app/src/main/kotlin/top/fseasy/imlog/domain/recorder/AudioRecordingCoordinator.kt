package top.fseasy.imlog.domain.recorder

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class VoiceRecordingCoordinator(
    // 依赖注入：由各平台提供具体实现
    private val recorder: AudioRecorder,
    private val guardian: RecordingGuardian = RecordingGuardian.NoOp,
) {

  private val _sessionState = MutableStateFlow<RecordingSessionState>(RecordingSessionState.Idle)
  val sessionState: StateFlow<RecordingSessionState> = _sessionState.asStateFlow()

  // 代理底层波形与时长
  val recordingSnapshot: StateFlow<RecordingSnapshot> = recorder.snapshot

  /** 按下 Hold：极速轻量启动，不惊动保活守护者 */
  suspend fun onHoldDown(destinationPath: String) {
    var shouldStart = false
    _sessionState.update { current ->
      if (current is RecordingSessionState.Idle) {
        shouldStart = true
        RecordingSessionState.Holding
      } else {
        current
      }
    }
    if (shouldStart) {
      recorder.start(destinationPath)
    }
  }

  /** 手指松开：如果处于 Hold，正常停止并返回结果 */
  suspend fun onHoldRelease(): AudioRecordResult? {
    var shouldStop = false
    _sessionState.update { current ->
      if (current is RecordingSessionState.Holding) {
        shouldStop = true
        RecordingSessionState.Idle
      } else {
        current
      }
    }
    return if (shouldStop) recorder.stop() else null
  }

  /** 滑动放弃：如果处于 Hold，直接取消删除临时文件 */
  suspend fun onHoldCancel() {
    var shouldCancel = false
    _sessionState.update { current ->
      if (current is RecordingSessionState.Holding) {
        shouldCancel = true
        RecordingSessionState.Idle
      } else {
        current
      }
    }
    if (shouldCancel) {
      recorder.cancel()
    }
  }

  /** 上滑锁定手势：从 Hold 提权到 Locked */
  fun onLockGesture() {
    var promoted = false
    _sessionState.update { current ->
      if (current is RecordingSessionState.Holding) {
        promoted = true
        RecordingSessionState.Locked(isPaused = false)
      } else {
        current
      }
    }
    if (promoted) {
      // 通知平台保活机制就位（Android 挂前台服务，iOS 锁后台 AudioSession）
      guardian.startGuarding()
    }
  }

  /** 点按录音：直接以 Locked 模式启动 */
  suspend fun onTapToRecord(destinationPath: String) {
    var shouldStart = false
    _sessionState.update { current ->
      if (current is RecordingSessionState.Idle) {
        shouldStart = true
        RecordingSessionState.Locked(isPaused = false)
      } else {
        current
      }
    }
    if (shouldStart) {
      guardian.startGuarding()
      recorder.start(destinationPath)
    }
  }

  suspend fun pause() {
    var shouldPause = false
    _sessionState.update { current ->
      if (current is RecordingSessionState.Locked && !current.isPaused) {
        shouldPause = true
        current.copy(isPaused = true)
      } else {
        current
      }
    }
    if (shouldPause) {
      recorder.pause()
    }
  }

  suspend fun resume() {
    var shouldResume = false
    _sessionState.update { current ->
      if (current is RecordingSessionState.Locked && current.isPaused) {
        shouldResume = true
        current.copy(isPaused = false)
      } else {
        current
      }
    }
    if (shouldResume) {
      recorder.resume()
    }
  }

  suspend fun stopLockedAndSend(): AudioRecordResult? {
    var shouldStop = false
    _sessionState.update { current ->
      if (current is RecordingSessionState.Locked) {
        shouldStop = true
        RecordingSessionState.Idle
      } else {
        current
      }
    }
    return if (shouldStop) {
      guardian.stopGuarding()
      recorder.stop()
    } else {
      null
    }
  }

  suspend fun cancelLocked() {
    var shouldCancel = false
    _sessionState.update { current ->
      if (current is RecordingSessionState.Locked) {
        shouldCancel = true
        RecordingSessionState.Idle
      } else {
        current
      }
    }
    if (shouldCancel) {
      guardian.stopGuarding()
      recorder.cancel()
    }
  }
}
