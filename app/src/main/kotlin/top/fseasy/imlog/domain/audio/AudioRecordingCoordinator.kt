package top.fseasy.imlog.domain.audio

import kotlinx.coroutines.flow.StateFlow
import top.fseasy.imlog.domain.model.StoragePathModel

class VoiceRecordingCoordinator(
    private val recorder: AudioRecorder,
    private val guardian: RecordingGuardian = RecordingGuardian.NoOp,
) {
  val outputAudioFileExtension: String
    get() = recorder.outputAudioFileExtension

  val recordingSnapshot: StateFlow<RecordingSnapshot> = recorder.snapshot

  /** Safe to run in Main thread: run IO in io threads. */
  suspend fun onHoldStart(
      destinationPath: StoragePathModel.Internal,
      onOsEvent: (RecordingOsEvent) -> Unit,
  ): Result<Unit> {
    return recorder.start(destinationPath, onOsEvent = onOsEvent)
  }

  suspend fun onHoldStop(): Result<AudioRecordResult> {
    return recorder.stop()
  }

  /**
   * Cancel action Run in IO thread for necessary parts.
   *
   * No exception will be thrown.
   */
  suspend fun onHoldCancel(): Result<AudioRecordCancelResult> {
    return recorder.cancel()
  }

  /** Sync fun. No Exception. */
  fun onTransformHoldToLock(prompt: RecordingPrompt) {
    return guardian.startGuarding(prompt)
  }

  /** 5. 直接开始长录音: 失败必须停掉保活，防止前台服务泄漏 */
  suspend fun onDirectLock(
      prompt: RecordingPrompt,
      destinationPath: StoragePathModel.Internal,
      onOsEvent: (RecordingOsEvent) -> Unit,
  ): Result<Unit> {
    guardian.startGuarding(prompt)
    return recorder.start(destinationPath, onOsEvent).onFailure {
      // 启动失败，必须关闭保活并回滚状态！
      guardian.stopGuarding()
    }
  }

  suspend fun onStopLocked(): Result<AudioRecordResult> {
    guardian.stopGuarding()
    return recorder.stop()
  }

  suspend fun onCancelLocked(): Result<AudioRecordCancelResult> {
    guardian.stopGuarding()
    return recorder.cancel()
  }
}
