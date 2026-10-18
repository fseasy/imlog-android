package top.fseasy.imlog.features.home.topiclog.composer2

import android.os.Parcelable
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize
import top.fseasy.imlog.R
import top.fseasy.imlog.features.home.topiclog.composer.MessageInputModeUi
import top.fseasy.imlog.ui.model.ImageVectorRes

@Parcelize
enum class ComposerStage : Parcelable {
  Default,
  Recording,
  Attachment,
}

fun MessageInputModeUi?.decideComposerStage() =
    when (this) {
      null -> ComposerStage.Default
      MessageInputModeUi.Text -> ComposerStage.Default
      MessageInputModeUi.HoldToTalk -> ComposerStage.Default
      MessageInputModeUi.VoiceRecording -> ComposerStage.Recording
      MessageInputModeUi.Camera -> ComposerStage.Recording
      MessageInputModeUi.Attachment -> ComposerStage.Attachment
    }

/** 免提录音状态 (P7 / P8) */
sealed interface TapRecordingState {
  object Idle : TapRecordingState

  data class Recording(val durationSec: Int) : TapRecordingState

  data class Paused(val durationSec: Int, val isPlayingPreview: Boolean = false) : TapRecordingState
}

/** 长按手势判决 (P9 弧形浮层) */
enum class HoldToTalkGestureTarget {
  Normal,
  Cancel,
  Lock,
}

/** 附件类型 */
enum class AttachmentType {
  Album,
  Audio,
  GenericFile,
}

@Immutable
data class ComposerInputModeContextActionUiState(
    val compressAlbumEnabled: Boolean,
)

@Immutable
data class Composer2BottomBarUiState(
    val currentStage: ComposerStage,
    val sendEnabled: Boolean,
    val contextActionState: ComposerInputModeContextActionUiState,
)

sealed interface ComposerAction {
  data class SelectInputMode(val mode: MessageInputModeUi) : ComposerAction

  data class SelectComposerStage(val stage: ComposerStage) : ComposerAction
}

sealed interface ComposerTextModeAction : ComposerAction {
  data class UpdateInputText(val text: String) : ComposerTextModeAction

  data object SendText : ComposerTextModeAction
}

sealed interface ComposerHoldToTalkModeAction : ComposerAction {

  data object StartHoldToTalk : ComposerHoldToTalkModeAction

  data object SendHoldToTalk : ComposerHoldToTalkModeAction

  data class ChangeHoldToTalkGesture(val gestureTarget: HoldToTalkGestureTarget) :
      ComposerHoldToTalkModeAction
}

sealed interface ComposerVoiceRecordingModeAction : ComposerAction {
  data object StartVoiceRecording : ComposerVoiceRecordingModeAction

  data object PauseVoiceRecording : ComposerVoiceRecordingModeAction

  data object ResumeVoiceRecording : ComposerVoiceRecordingModeAction

  data object DiscardVoiceRecording : ComposerVoiceRecordingModeAction

  data object ToggleVoiceRecordingPreviewPlay : ComposerVoiceRecordingModeAction

  data object SendVoiceRecording : ComposerVoiceRecordingModeAction
}

sealed interface ComposerAttachmentModeAction : ComposerAction {
  data class SelectAlbums(val uris: List<android.net.Uri>) : ComposerAttachmentModeAction

  data class SelectAudios(val uris: List<android.net.Uri>) : ComposerAttachmentModeAction

  data class SelectFiles(val uris: List<android.net.Uri>) : ComposerAttachmentModeAction
}

sealed interface ComposerBottomContextAction : ComposerAction {
  data object ToggleCompressAlbum : ComposerBottomContextAction // 例如：未来增加的高清开关
}

@Immutable
internal data class StageSelectorBarItemUiModel(
    val stage: ComposerStage,
    val iconVector: ImageVectorRes,
    @StringRes val contentDescriptionResId: Int,
)

internal val stageSelectorBarItems =
    listOf(
        StageSelectorBarItemUiModel(
            stage = ComposerStage.Default,
            ImageVectorRes.ResId(R.drawable.icon_chat_bubble_48px),
            R.string.composer_input_mode_default_desc,
        ),
        StageSelectorBarItemUiModel(
            ComposerStage.Recording,
            ImageVectorRes.ResId(R.drawable.icon_radio_button_checked_48px),
            R.string.composer_input_mode_recorder_desc,
        ),
        StageSelectorBarItemUiModel(
            ComposerStage.Attachment,
            ImageVectorRes.ResId(R.drawable.icon_attach_file_48px),
            R.string.composer_input_mode_attachment_desc,
        ),
    )
