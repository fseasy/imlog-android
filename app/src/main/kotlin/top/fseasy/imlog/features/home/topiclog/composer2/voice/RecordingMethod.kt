package top.fseasy.imlog.features.home.topiclog.composer2.voice

import androidx.compose.runtime.Immutable
import top.fseasy.imlog.domain.audio.RecordingOsEvent
import top.fseasy.imlog.domain.audio.Waveform
import top.fseasy.imlog.domain.model.VoiceDraft

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

internal enum class RecordingMethod {
  Holding,
  Locked,
}

enum class ActiveRecordingPendingAction {
  StartOsNative,
  CancelOnRace,
  Cancel,
  Stop,
  StopOnRace
}

/**
 * State rolling:
 *
 * Idle -> active-recording -> \[committed-draft\] -> CleanUp -> Idle
 */
@Immutable
internal sealed interface VoiceDraftUiState {

  @Immutable data object Idle : VoiceDraftUiState

  @Immutable data object CleanUp : VoiceDraftUiState

  @Immutable
  sealed interface ActiveRecording : VoiceDraftUiState {

    val draft: VoiceDraft.ActiveRecording

    /** To distinguish the recording session on onOsEvent callback condition */
    val sessionToken: Any

    val pendingAction: ActiveRecordingPendingAction?

    // Keep 2 subtypes to explicit the `draft` type.
    @Immutable
    data class Holding(
        override val draft: VoiceDraft.ActiveRecording.Base,
        override val sessionToken: Any,
        override val pendingAction: ActiveRecordingPendingAction?,
    ) : ActiveRecording

    @Immutable
    data class Locked(
        override val draft: VoiceDraft.ActiveRecording,
        override val sessionToken: Any,
        override val pendingAction: ActiveRecordingPendingAction?,
    ) : ActiveRecording
  }

  @Immutable data class CommittedDraft(val draft: VoiceDraft.Committed) : VoiceDraftUiState
}

/**
 * 需要给这个 interface 增加一个 copy 函数，让 copy 调用处更简单。 但因为 draft 类型不同，所以 draft 不能暴露出来，且因为 draft
 * 就是子类型存在的唯一区别，所以不暴露 ok。
 */
internal fun VoiceDraftUiState.ActiveRecording.withMeta(
    sessionToken: Any = this.sessionToken,
    pendingAction: ActiveRecordingPendingAction? = this.pendingAction,
): VoiceDraftUiState.ActiveRecording =
    when (this) {
      is VoiceDraftUiState.ActiveRecording.Holding ->
          copy(
              sessionToken = sessionToken,
              pendingAction = pendingAction,
          )
      is VoiceDraftUiState.ActiveRecording.Locked ->
          copy(
              sessionToken = sessionToken,
              pendingAction = pendingAction,
          )
    }

internal fun VoiceDraftUiState.ActiveRecording.method() =
    when (this) {
      is VoiceDraftUiState.ActiveRecording.Holding -> RecordingMethod.Holding
      is VoiceDraftUiState.ActiveRecording.Locked -> RecordingMethod.Locked
    }

internal data class RecordingOsEventSession(
    val event: RecordingOsEvent,
    val sessionToken: Any,
)
