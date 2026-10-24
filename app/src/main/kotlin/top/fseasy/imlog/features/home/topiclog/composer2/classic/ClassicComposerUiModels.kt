package top.fseasy.imlog.features.home.topiclog.composer2.classic

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import top.fseasy.imlog.domain.model.ScreenOrientation

enum class ActivePanel {
  NONE, // 啥面板也不要（可能在看聊天记录，或者纯软键盘输入）
  ATTACHMENT, // 附件面板
  // 未来可平滑扩展：EMOJI, AUDIO_STUDIO
}

fun ActivePanel.isAttachmentActive() = this == ActivePanel.ATTACHMENT

enum class ImeTargetState {
  CLOSED, // target == 0（已关闭或正在收起）
  OPEN, // target > 0（已打开或正在弹起）
}

enum class ClassicComposerMode {
  Default,
  HoldToRecord,
  VoiceNote,
}

@Immutable
data class ComposerDockState(
  val mode: ClassicComposerMode = ClassicComposerMode.Default,
  val activePanel: ActivePanel = ActivePanel.NONE,
  val cachedImeHeightInDp: ImeHeightInDp = ImeHeightInDpDefaults,
)

@Immutable
data class ImeHeightInDp(
    val portrait: Float,
    val landscape: Float,
)

val ImeHeightInDpDefaults = ImeHeightInDp(260f, 100f)

/** @return height in dp for specific orientation */
fun ImeHeightInDp.get(orientation: ScreenOrientation) =
    when (orientation) {
      ScreenOrientation.Portrait -> portrait
      ScreenOrientation.Landscape -> landscape
    }

fun ImeHeightInDp.with(orientation: ScreenOrientation, value: Float) =
    when (orientation) {
      ScreenOrientation.Portrait -> copy(portrait = value)
      ScreenOrientation.Landscape -> copy(landscape = value)
    }

sealed interface ClassicComposerAction {}

internal interface ComposerDockAction : ClassicComposerAction {
  data class UpdateImeHeight(val orientation: ScreenOrientation, val heightInDp: Float) :
      ComposerDockAction
}

internal sealed interface ComposerHoldToRecordAction : ClassicComposerAction {
  data class StartHoldToTalk(val rootOffset: Offset) : ComposerHoldToRecordAction

  data class ReleaseHoldToTalk(val rootOffset: Offset) : ComposerHoldToRecordAction

  data class MoveHoldToTalkTouch(val rootOffset: Offset) : ComposerHoldToRecordAction
}

internal sealed interface ComposerBarAction : ClassicComposerAction {

  data class ChangeFocus(val focused: Boolean) : ComposerBarAction

  data object SendText : ComposerBarAction
}

sealed interface ComposerVoiceRecordingAction : ClassicComposerAction {
  data object StartVoiceRecording : ComposerVoiceRecordingAction

  data object PauseVoiceRecording : ComposerVoiceRecordingAction

  data object ResumeVoiceRecording : ComposerVoiceRecordingAction

  data object DiscardVoiceRecording : ComposerVoiceRecordingAction

  data object ToggleVoiceRecordingPreviewPlay : ComposerVoiceRecordingAction

  data object SendVoiceRecording : ComposerVoiceRecordingAction
}

sealed interface ComposerAttachmentStageAction : ClassicComposerAction {
  data class SelectAlbums(val uris: List<android.net.Uri>) : ComposerAttachmentStageAction

  data class SelectAudios(val uris: List<android.net.Uri>) : ComposerAttachmentStageAction

  data class SelectFiles(val uris: List<android.net.Uri>) : ComposerAttachmentStageAction
}
