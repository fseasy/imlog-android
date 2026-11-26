package top.fseasy.imlog.features.home.conversation.composer2.voice

import androidx.compose.runtime.Immutable
import top.fseasy.imlog.domain.audio.RecordingOsEvent
import top.fseasy.imlog.domain.audio.Waveform
import top.fseasy.imlog.domain.model.StoragePathModel
import top.fseasy.imlog.domain.model.VoiceDraft
import top.fseasy.imlog.domain.usecase.composer.VoiceDraftUseCase

@Immutable
data class VoicePlayerUiState(
    val durationText: String,
    val waveform: Waveform, // 已经适配当前设备宽度的柱状高度列表
    val playbackProgress: Float,
    val isPlaying: Boolean = false,
)

// 宏观模式：只保留结构性数据，低频：只有切模式时才发射
sealed interface VoiceComposerMode {
  data object Idle : VoiceComposerMode

  data object Holding : VoiceComposerMode

  data class ActiveRecording(
      val baseWaveform: Waveform?, // 历史底稿波形，静态数据
  ) : VoiceComposerMode

  data class Draft(val playerUiState: VoicePlayerUiState) : VoiceComposerMode
}

sealed interface ActiveRecordingAction

sealed interface BeforeOsNativeStartedAction : ActiveRecordingAction {
  data object StartOsNative : BeforeOsNativeStartedAction

  data object CancelOnceStarted : BeforeOsNativeStartedAction

  data object StopOnceStarted : BeforeOsNativeStartedAction
}

sealed interface OsNativeStartedAction : ActiveRecordingAction {
  data object Recording : OsNativeStartedAction

  data object Cancel : OsNativeStartedAction

  data object Stop : OsNativeStartedAction
}

sealed interface OsNativeStartFailedAction : ActiveRecordingAction {
  data object CancelOnStartFailed : OsNativeStartFailedAction

  data object StopOnStartFailed : OsNativeStartFailedAction

  data object RollbackOnStartFailed : OsNativeStartFailedAction
}

fun BeforeOsNativeStartedAction.nextOnStartSuccess(): OsNativeStartedAction =
    when (this) {
      BeforeOsNativeStartedAction.StartOsNative -> OsNativeStartedAction.Recording
      BeforeOsNativeStartedAction.CancelOnceStarted -> OsNativeStartedAction.Cancel
      BeforeOsNativeStartedAction.StopOnceStarted -> OsNativeStartedAction.Stop
    }

fun BeforeOsNativeStartedAction.nextOnStartFailure(): OsNativeStartFailedAction =
    when (this) {
      BeforeOsNativeStartedAction.StartOsNative -> OsNativeStartFailedAction.RollbackOnStartFailed
      BeforeOsNativeStartedAction.CancelOnceStarted -> OsNativeStartFailedAction.CancelOnStartFailed
      BeforeOsNativeStartedAction.StopOnceStarted -> OsNativeStartFailedAction.StopOnStartFailed
    }

fun BeforeOsNativeStartedAction.nextOnStartResult(startedSuccess: Boolean): ActiveRecordingAction =
    if (startedSuccess) nextOnStartSuccess() else nextOnStartFailure()

/**
 * 用户点击 cancel 后的下一个动作
 *
 * @return null 表示初始状态无效，不能转移
 */
fun ActiveRecordingAction.nextOnUserCancel() =
    when (this) {
      BeforeOsNativeStartedAction.StartOsNative -> BeforeOsNativeStartedAction.CancelOnceStarted
      OsNativeStartedAction.Recording -> OsNativeStartedAction.Cancel
      else -> null
    }

/**
 * 用户触发 Stop 后的下一个动作.
 *
 * @return null 表示初始状态无效，不能转移
 */
fun ActiveRecordingAction.nextOnUserStop() =
    when (this) {
      BeforeOsNativeStartedAction.StartOsNative -> BeforeOsNativeStartedAction.StopOnceStarted
      OsNativeStartedAction.Recording -> OsNativeStartedAction.Stop
      else -> null
    }

/**
 * State rolling:
 *
 * Initializing ->Idle -> active-recording -> \[committed-draft\] -> CleanUp -> Idle
 */
@Immutable
internal sealed interface VoiceDraftUiState {

  @Immutable data object Initializing : VoiceDraftUiState

  @Immutable data object Idle : VoiceDraftUiState

  @Immutable
  sealed interface ActiveRecording : VoiceDraftUiState {

    val draft: VoiceDraft.ActiveRecording

    /** To distinguish the recording session on onOsEvent callback condition */
    val sessionToken: Any

    val action: ActiveRecordingAction

    // Keep 2 subtypes to explicit the `draft` type.
    @Immutable
    data class Holding(
        override val draft: VoiceDraft.ActiveRecording.Base,
        override val sessionToken: Any,
        override val action: ActiveRecordingAction,
    ) : ActiveRecording

    @Immutable
    data class Locked(
        override val draft: VoiceDraft.ActiveRecording,
        override val sessionToken: Any,
        override val action: ActiveRecordingAction,
    ) : ActiveRecording
  }

  @Immutable data class CommittedDraft(val draft: VoiceDraft.Committed) : VoiceDraftUiState
}

internal enum class RecordingCancelPolicy {
  /** 用户主动取消，所以要彻底丢弃：删除当前片段 + 历史底稿（如果有），最终必回 Idle */
  UserCancelCausedDiscardAll,
  /** 无法正常 stop, 调用 cancel 来安全停止。此时仅回滚本次续录：只删当前片段；若存在历史底稿则恢复为 CommittedDraft，否则回 Idle */
  InternalCancelCausedRollback,
}

/**
 * Decide the cancel policy when
 * 1. Cancel action is triggered
 * 2. Every possible failures
 */
internal val ActiveRecordingAction.cancelPolicy: RecordingCancelPolicy
  get() =
      when (this) {
        BeforeOsNativeStartedAction.CancelOnceStarted,
        OsNativeStartedAction.Cancel,
        OsNativeStartFailedAction.CancelOnStartFailed ->
            RecordingCancelPolicy.UserCancelCausedDiscardAll

        BeforeOsNativeStartedAction.StartOsNative,
        BeforeOsNativeStartedAction.StopOnceStarted,
        OsNativeStartedAction.Recording,
        OsNativeStartedAction.Stop, // Stop while encounter error
        OsNativeStartFailedAction.RollbackOnStartFailed,
        OsNativeStartFailedAction.StopOnStartFailed ->
            RecordingCancelPolicy.InternalCancelCausedRollback
      }

/**
 * 需要给这个 interface 增加一个 copy 函数，让 copy 调用处更简单。 但因为 draft 类型不同，所以 draft 不能暴露出来，且因为 draft
 * 就是子类型存在的唯一区别，所以不暴露 ok。
 */
internal fun VoiceDraftUiState.ActiveRecording.withMeta(
    sessionToken: Any = this.sessionToken,
    action: ActiveRecordingAction = this.action,
): VoiceDraftUiState.ActiveRecording =
    when (this) {
      is VoiceDraftUiState.ActiveRecording.Holding ->
          copy(
              sessionToken = sessionToken,
              action = action,
          )
      is VoiceDraftUiState.ActiveRecording.Locked ->
          copy(
              sessionToken = sessionToken,
              action = action,
          )
    }

internal fun VoiceDraftUiState.isActiveRecordingLockedAppending() =
    (this as? VoiceDraftUiState.ActiveRecording.Locked)?.draft is
        VoiceDraft.ActiveRecording.Appending

internal fun VoiceDraftUiState.ActiveRecording.Holding.toLockedIfPossible():
    VoiceDraftUiState.ActiveRecording.Locked? {
  val couldTransit =
      when (this.action) {
        // No mater if it's waiting for recorder starting, draftState can always
        // transit to the Locked State.
        // But if Cancel/Stop requested or Start failed, we reject.
        BeforeOsNativeStartedAction.StartOsNative,
        OsNativeStartedAction.Recording -> true
        BeforeOsNativeStartedAction.CancelOnceStarted,
        BeforeOsNativeStartedAction.StopOnceStarted,
        is OsNativeStartFailedAction,
        OsNativeStartedAction.Cancel,
        OsNativeStartedAction.Stop -> false
      }
  return if (couldTransit)
      VoiceDraftUiState.ActiveRecording.Locked(
          action = action,
          draft = draft,
          sessionToken = sessionToken,
      )
  else null
}

/**
 * Once OsNative Recorder started, decide the next state on [fromState] and [startedSuccess]
 *
 * @return if null, from state is invalid and can't transit.
 *
 * Safe for main-thread. No exception except CancellationException
 */
internal fun VoiceDraftUiState.ActiveRecording.nextOnceOsNativeStarted(
    startedSuccess: Boolean,
): VoiceDraftUiState.ActiveRecording? {
  val validBaseAction = this.action as? BeforeOsNativeStartedAction ?: return null
  val nextAction = validBaseAction.nextOnStartResult(startedSuccess)
  return this.withMeta(action = nextAction)
}

/**
 * 注意，这里还是在 [VoiceDraftUiState.ActiveRecording] 的范畴，只是计算用户点击 Cancel 后，当前状态能否转移到 Cancel 的状态，若可以， 就将
 * Cancel 动作放到 [VoiceDraftUiState.ActiveRecording] 之上。返回的，是转移后的状态。
 */
internal fun VoiceDraftUiState.ActiveRecording.nextOnUserCancel():
    VoiceDraftUiState.ActiveRecording? {
  val nextAction = this.action.nextOnUserCancel() ?: return null
  return this.withMeta(action = nextAction)
}

internal fun VoiceDraftUiState.ActiveRecording.nextOnUserStop():
    VoiceDraftUiState.ActiveRecording? {
  val nextAction = this.action.nextOnUserStop() ?: return null
  return this.withMeta(action = nextAction)
}

internal data class CommitActiveRecordingOnCancelDoneResult(
    val state: VoiceDraftUiState,
    val filesToDelete: List<StoragePathModel.Internal>,
)

/**
 * 这计算的是，Cancel 执行**完成后**，应该转移的状态、删除的文件。区别于 [nextOnUserCancel], 它是算 Cancel 触发 时能不能转移到 Cancel,
 * 能转移时对应的 guarding 状态，尚没有执行 Cancel
 *
 * Pure CPU, no IO, no Exception.
 */
internal fun commitActiveRecordingOnCancelDone(
    currentState: VoiceDraftUiState.ActiveRecording,
): CommitActiveRecordingOnCancelDoneResult {
  val shouldDiscardAll =
      when (currentState.action) {
        // The following all are user intentional triggered actions. We should discard all files and
        // back to Idle.
        BeforeOsNativeStartedAction.CancelOnceStarted,
        OsNativeStartedAction.Cancel,
        OsNativeStartFailedAction.CancelOnStartFailed -> true
        // The following actions, means the cancel is triggered by Internal Failure / Os Event.
        // Not user intentional. So we should do rollback
        BeforeOsNativeStartedAction.StartOsNative,
        BeforeOsNativeStartedAction.StopOnceStarted,
        OsNativeStartedAction.Recording,
        OsNativeStartedAction.Stop,
        OsNativeStartFailedAction.RollbackOnStartFailed,
        OsNativeStartFailedAction.StopOnStartFailed -> false
      }

  val draft = currentState.draft

  return if (shouldDiscardAll) {
    // 用户主动取消：丢弃 base（如果有）+ 本次 recording
    val filesToDelete =
        when (draft) {
          is VoiceDraft.ActiveRecording.Appending ->
              listOf(draft.base.filePath, draft.recordingFilePath)
          is VoiceDraft.ActiveRecording.Base -> listOf(draft.recordingFilePath)
        }
    CommitActiveRecordingOnCancelDoneResult(VoiceDraftUiState.Idle, filesToDelete)
  } else {
    // 非用户主动：回滚，只删本次 recording，保留 base（如果有），并回到 base 对应的状态（没有就是 Idle）
    // 复用 UseCase 的逻辑
    val commitResult = VoiceDraftUseCase.commitByRollbackOnRecordingFailure(draft)
    val resultState = if (commitResult.draft != null) {
      VoiceDraftUiState.CommittedDraft(commitResult.draft)
    } else {
      VoiceDraftUiState.Idle
    }
    CommitActiveRecordingOnCancelDoneResult(resultState, commitResult.filesToDelete)
  }
}

/** Safe for main-thread. No exception except CancellationException */
internal suspend fun VoiceDraftUiState.ActiveRecording.rollbackOnFailure(
    draftUseCase: VoiceDraftUseCase,
): VoiceDraftUiState {
  val committedDraft = draftUseCase.tryCommitWhenRecordingFailure(this.draft)
  return if (committedDraft != null) VoiceDraftUiState.CommittedDraft(committedDraft)
  else VoiceDraftUiState.Idle
}

/** Safe for main-thread. No exception except CancellationException */
internal suspend fun VoiceDraftUiState.discardAll(
    draftUseCase: VoiceDraftUseCase,
): VoiceDraftUiState.Idle {
  when (this) {
    is VoiceDraftUiState.ActiveRecording -> draftUseCase.deleteDraftFiles(this.draft)
    is VoiceDraftUiState.CommittedDraft -> draftUseCase.deleteDraftFiles(this.draft)
    VoiceDraftUiState.Idle -> Unit
    VoiceDraftUiState.Initializing -> Unit
  }
  return VoiceDraftUiState.Idle
}

internal data class RecordingOsEventSession(
    val event: RecordingOsEvent,
    val sessionToken: Any,
)
