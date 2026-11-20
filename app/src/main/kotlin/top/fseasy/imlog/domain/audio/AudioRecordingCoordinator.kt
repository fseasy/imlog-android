package top.fseasy.imlog.domain.audio

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlinx.coroutines.flow.StateFlow
import top.fseasy.imlog.domain.model.StoragePathModel

@OptIn(ExperimentalAtomicApi::class)
class VoiceRecordingCoordinator(
    private val recorder: AudioRecorder,
    private val guardian: RecordingGuardian = RecordingGuardian.NoOp,
) {

  val outputAudioFileExtension: String
    get() = recorder.outputAudioFileExtension

  val recordingSnapshot: StateFlow<RecordingSnapshot> = recorder.snapshot

  private val isGuardingStarted = AtomicBoolean(false)

  /**
   * Safe to run in Main thread: run IO in io threads.
   *
   * 注意，启动可能需要一定时间，业务使用时，必须考虑到快按快停的竞态：在服务未启动前，用户已经触发了 stop/cancel. 这时，千万不要直接调 [stop]/[cancel].
   * 一方面底层可能会抛异常(android 侧会校验是否有 active recording),
   * 另一方面，就算底层静默返回，你这次调用也没有真正关闭服务——服务随后可能会正常起来，这时你就关不掉了。
   *
   * 所以，业务侧一定要考虑到这个竞态情况。
   *
   * @throws kotlin.coroutines.cancellation.CancellationException
   */
  suspend fun start(
      destinationPath: StoragePathModel.Internal,
      onOsEvent: (RecordingOsEvent) -> Unit,
  ): Result<Unit> {
    return recorder.start(destinationPath, onOsEvent = onOsEvent)
  }

  /**
   * Sync fun. 幂等，调用多次只有第一次实际调用
   *
   * No Exception.
   */
  fun guarding(prompt: RecordingPrompt) {
    if (isGuardingStarted.compareAndSet(expectedValue = false, newValue = true)) {
      guardian.startGuarding(prompt)
    }
  }

  /**
   * Sync function. 幂等（调用多次只有第一次实际调用），未 guarding 时调用也无害
   *
   * No exception will be thrown.
   *
   * Call this if you:
   * 1. call [start]
   * 2. call [guarding] before [start] success
   * 3. now you know [start] failed, you should call [stopGuarding]. you shouldn't call
   *    [stop]/[cancel] in this condition as recorder will raise exception if [start] not succeeded.
   */
  fun stopGuarding() {
    if (isGuardingStarted.compareAndSet(expectedValue = true, newValue = false)) {
      guardian.stopGuarding()
    }
  }

  /**
   * start + guarding
   *
   * @throws kotlin.coroutines.cancellation.CancellationException
   */
  suspend fun startAndGuarding(
      prompt: RecordingPrompt,
      destinationPath: StoragePathModel.Internal,
      onOsEvent: (RecordingOsEvent) -> Unit,
  ): Result<Unit> {
    // NOTE: we should first start, then guarding after starting success.
    return start(destinationPath, onOsEvent).onSuccess {
      guarding(prompt)
    }
  }

  /** @throws kotlin.coroutines.cancellation.CancellationException */
  suspend fun stop(): Result<AudioRecordResult> {
    try {
      return recorder.stop()
    } finally {
      stopGuarding()
    }
  }

  /**
   * Cancel action Run in IO thread for necessary parts.
   *
   * @throws kotlin.coroutines.cancellation.CancellationException
   */
  suspend fun cancel(): Result<AudioRecordCancelResult> {
    try {
      return recorder.cancel()
    } finally {
      stopGuarding()
    }
  }
}
