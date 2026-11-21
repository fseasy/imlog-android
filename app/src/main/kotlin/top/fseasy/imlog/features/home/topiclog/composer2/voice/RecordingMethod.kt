package top.fseasy.imlog.features.home.topiclog.composer2.voice

import androidx.compose.runtime.Immutable
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
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

fun BeforeOsNativeStartedAction.next(startedSuccess: Boolean): ActiveRecordingAction =
    if (startedSuccess) nextOnStartSuccess() else nextOnStartFailure()

/**
 * State rolling:
 *
 * Idle -> active-recording -> \[committed-draft\] -> CleanUp -> Idle
 */
@Immutable
internal sealed interface VoiceDraftUiState {

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

@OptIn(ExperimentalContracts::class)
internal fun VoiceDraftUiState.isOnCancelRace(): Boolean {
  contract {
    returns(true) implies (this@isOnCancelRace is VoiceDraftUiState.ActiveRecording)
  }
  return (this as? VoiceDraftUiState.ActiveRecording)?.action is
      BeforeOsNativeStartedAction.CancelOnceStarted
}

@OptIn(ExperimentalContracts::class)
internal fun VoiceDraftUiState.isOnStopRace(): Boolean {
  contract {
    returns(true) implies (this@isOnStopRace is VoiceDraftUiState.ActiveRecording)
  }
  return (this as? VoiceDraftUiState.ActiveRecording)?.action ==
      BeforeOsNativeStartedAction.StopOnceStarted
}

internal fun VoiceDraftUiState.isActiveRecordingLockedAppending() =
    (this as? VoiceDraftUiState.ActiveRecording.Locked)?.draft is
        VoiceDraft.ActiveRecording.Appending

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
  val nextAction = validBaseAction.next(startedSuccess)
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
  }
  return VoiceDraftUiState.Idle
}

internal data class RecordingOsEventSession(
    val event: RecordingOsEvent,
    val sessionToken: Any,
)
