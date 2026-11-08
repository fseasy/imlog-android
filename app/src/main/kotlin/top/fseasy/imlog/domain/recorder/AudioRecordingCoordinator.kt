package top.fseasy.imlog.domain.recorder

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import top.fseasy.imlog.domain.model.StoragePathModel

class VoiceRecordingCoordinator(
    private val recorder: AudioRecorder,
    private val guardian: RecordingGuardian = RecordingGuardian.NoOp,
) {
  val outputAudioFileExtension: String
    get() = recorder.outputAudioFileExtension

  private val _sessionState = MutableStateFlow<RecordingSessionState>(RecordingSessionState.Idle)
  val sessionState: StateFlow<RecordingSessionState> = _sessionState.asStateFlow()

  val recordingSnapshot: StateFlow<RecordingSnapshot> = recorder.snapshot

  /** 1. 按下 Hold: 纯 CAS 比较，失败回滚状态，透传 Result */
  suspend fun onHoldDown(destinationPath: StoragePathModel.Internal): Result<Unit> {
    if (!_sessionState.compareAndSet(RecordingSessionState.Idle, RecordingSessionState.Holding)) {
      return Result.failure(IllegalStateException("Cannot hold to record when not in Idle state"))
    }

    return recorder
        .start(destinationPath)
        .onSuccess {
          // 防御极速取消：如果启动完成前，外部就已经把状态取消成 Idle 了，立刻补杀底层录音
          if (_sessionState.value == RecordingSessionState.Idle) {
            recorder.cancel()
          }
        }
        .onFailure {
          // 启动失败，原子回滚状态，防止状态死锁在 Holding
          _sessionState.compareAndSet(RecordingSessionState.Holding, RecordingSessionState.Idle)
        }
  }

  /** 2. 松开 Hold: 纯 CAS 比较 */
  suspend fun onHoldRelease(): AudioRecordResult? {
    return if (
        _sessionState.compareAndSet(RecordingSessionState.Holding, RecordingSessionState.Idle)
    ) {
      recorder.stop()
    } else {
      null
    }
  }

  /**
   * 取消 Hold: 纯 CAS 比较
   *
   * Cancel action Run in IO thread for necessary parts.
   *
   * No exception will be thrown.
   */
  suspend fun onHoldCancel(): AudioRecordResult.Failure? {
    return if (
        _sessionState.compareAndSet(RecordingSessionState.Holding, RecordingSessionState.Idle)
    ) {
      recorder.cancel()
    } else {
      null
    }
  }

  /** 4. 上滑锁定手势: 纯 CAS 争胜，手势与松开并发时，Lock 优先 */
  fun onHoldToLockGesture(prompt: RecordingPrompt): Boolean {
    val success =
        _sessionState.compareAndSet(
            RecordingSessionState.Holding,
            RecordingSessionState.Locked,
        )
    if (success) {
      guardian.startGuarding(prompt)
    }
    return success
  }

  /** 5. 直接开始长录音: 失败必须停掉保活，防止前台服务泄漏 */
  suspend fun onDirectLock(
      prompt: RecordingPrompt,
      destinationPath: StoragePathModel.Internal,
  ): Result<Unit> {
    if (!_sessionState.compareAndSet(RecordingSessionState.Idle, RecordingSessionState.Locked)) {
      return Result.failure(IllegalStateException("Cannot start voice note when not in Idle state"))
    }

    guardian.startGuarding(prompt)

    return recorder
        .start(destinationPath)
        .onSuccess {
          // 防御极速取消：如果启动期间已经被取消成了 Idle，立刻释放保活与录音
          if (_sessionState.value == RecordingSessionState.Idle) {
            guardian.stopGuarding()
            recorder.cancel()
          }
        }
        .onFailure {
          // 启动失败，必须关闭保活并回滚状态！
          guardian.stopGuarding()
          _sessionState.compareAndSet(RecordingSessionState.Locked, RecordingSessionState.Idle)
        }
  }

  /** 8. 锁定态完成并发送: getAndUpdate 原子提取前置状态 */
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

  /** 9. 锁定态取消: getAndUpdate 原子提取前置状态 */
  suspend fun onCancelLocked(): AudioRecordResult.Failure? {
    val previousState = _sessionState.getAndUpdate { current ->
      if (current is RecordingSessionState.Locked) RecordingSessionState.Idle else current
    }

    return if (previousState is RecordingSessionState.Locked) {
      guardian.stopGuarding()
      recorder.cancel()
    } else {
      null
    }
  }
}
