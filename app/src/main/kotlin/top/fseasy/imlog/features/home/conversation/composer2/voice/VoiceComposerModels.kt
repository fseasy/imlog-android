package top.fseasy.imlog.features.home.conversation.composer2.voice

import androidx.compose.runtime.Immutable
import top.fseasy.imlog.domain.audio.RecordingOsEvent
import top.fseasy.imlog.domain.audio.Waveform
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

fun ActiveRecordingAction.nextOnCancel() =
    when (this) {
      BeforeOsNativeStartedAction.StartOsNative -> BeforeOsNativeStartedAction.CancelOnceStarted
      OsNativeStartedAction.Recording -> OsNativeStartedAction.Cancel
      else -> null
    }

fun ActiveRecordingAction.nextOnStop() =
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
  /** 彻底丢弃：删除当前片段 + 历史底稿（如果有），最终必回 Idle */
  DiscardAll,
  /** 仅回滚本次续录：只删当前片段；若存在历史底稿则恢复为 CommittedDraft，否则回 Idle */
  RollbackCurrentSegment,
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
        OsNativeStartFailedAction.CancelOnStartFailed -> RecordingCancelPolicy.DiscardAll

        BeforeOsNativeStartedAction.StartOsNative,
        BeforeOsNativeStartedAction.StopOnceStarted,
        OsNativeStartedAction.Recording,
        OsNativeStartedAction.Stop, // Stop while encounter error
        OsNativeStartFailedAction.RollbackOnStartFailed,
        OsNativeStartFailedAction.StopOnStartFailed -> RecordingCancelPolicy.RollbackCurrentSegment
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

internal fun VoiceDraftUiState.ActiveRecording.nextOnCancel(): VoiceDraftUiState.ActiveRecording? {
  val nextAction = this.action.nextOnCancel() ?: return null
  return this.withMeta(action = nextAction)
}

internal fun VoiceDraftUiState.ActiveRecording.nextOnStop(): VoiceDraftUiState.ActiveRecording? {
  val nextAction = this.action.nextOnStop() ?: return null
  return this.withMeta(action = nextAction)
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
