package top.fseasy.imlog.domain.recorder

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/**
 * 业务上，录音有 2 种模式：
 *
 * 1. Hold To Record, 持续按压录音
 * 2. Voice Note, 点按录音，不需要持续；允许后台，所以要起一个 service
 *
 * 而且，我们允许 HoldToRecord -> VoiceNote, 转换过程中，要保证 AudioRecorder 不变（存活），只改变 ui，同时增加 一个 Service 保活。
 *
 * 这个类就是协调上述的业务逻辑：同时控制 recorder、service.
 *
 * A general implementation, intend for KMP migration.
 *
 * 依赖注入：由各平台提供具体实现, 为了跨平台，手工注入而不使用注入工具
 */
class VoiceRecordingCoordinator(
    private val recorder: AudioRecorder,
    private val guardian: RecordingGuardian = RecordingGuardian.NoOp,
) {

  fun generateDefaultFilename(prefix: String = "recording") =
      recorder.generateDefaultFilename(prefix)

  private val _sessionState = MutableStateFlow<RecordingSessionState>(RecordingSessionState.Idle)
  val sessionState: StateFlow<RecordingSessionState> = _sessionState.asStateFlow()

  val recordingSnapshot: StateFlow<RecordingSnapshot> = recorder.snapshot

  /** 1. 按下 Hold: 纯 CAS 比较 只有当前确实是 Idle 时，原子切换为 Holding 并开录 */
  suspend fun onHoldDown(destinationPath: String) {
    if (_sessionState.compareAndSet(RecordingSessionState.Idle, RecordingSessionState.Holding)) {
      recorder.start(destinationPath)
    }
  }

  /** 2. 松开 Hold: 纯 CAS 比较 只有当前确实是 Holding 时，原子切回 Idle 并停止落盘 */
  suspend fun onHoldRelease(): AudioRecordResult? {
    return if (
        _sessionState.compareAndSet(RecordingSessionState.Holding, RecordingSessionState.Idle)
    ) {
      recorder.stop()
    } else {
      null
    }
  }

  /** 3. 取消 Hold: 纯 CAS 比较 */
  suspend fun onHoldCancel() {
    if (_sessionState.compareAndSet(RecordingSessionState.Holding, RecordingSessionState.Idle)) {
      recorder.cancel()
    }
  }

  /** 4. 上滑锁定手势: 纯 CAS 比较 只有当前是 Holding，才原子切换到 Locked(isPaused = false)，并触发保活提权 */
  fun onHoldToLockGesture(prompt: RecordingPrompt) {
    if (_sessionState.compareAndSet(RecordingSessionState.Holding, RecordingSessionState.Locked)) {
      guardian.startGuarding(prompt)
    }
  }

  /** 5. 直接开始长录音: 纯 CAS 比较 */
  suspend fun onDirectLock(
      prompt: RecordingPrompt,
      destinationPath: String,
  ) {
    if (_sessionState.compareAndSet(RecordingSessionState.Idle, RecordingSessionState.Locked)) {
      guardian.startGuarding(prompt)
      recorder.start(destinationPath)
    }
  }

  /**
   * 8. 锁定态完成并发送: 提示：此处因为 Locked 可能是 isPaused=true 也可能是 false，
   *
   * 采用 getAndUpdate 一行代码兼容两种情况，比写两次 CAS 更加干净！
   */
  suspend fun onStopLocked(): AudioRecordResult? {
    val previousState = _sessionState.getAndUpdate { current ->
      if (current is RecordingSessionState.Locked) RecordingSessionState.Idle else current
    }

    return if (previousState is RecordingSessionState.Locked) {
      guardian.stopGuarding()
      recorder.stop()
    } else {
      null
    }
  }

  /** 9. 锁定态取消: */
  suspend fun onCancelLocked() {
    val previousState = _sessionState.getAndUpdate { current ->
      if (current is RecordingSessionState.Locked) RecordingSessionState.Idle else current
    }

    if (previousState is RecordingSessionState.Locked) {
      guardian.stopGuarding()
      recorder.cancel()
    }
  }
}
