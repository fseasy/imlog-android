package top.fseasy.imlog.features.home.topiclog.composer2

import android.os.Parcelable
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
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
      MessageInputModeUi.HoldToRecord -> ComposerStage.Default
      MessageInputModeUi.VoiceNote -> ComposerStage.Recording
      MessageInputModeUi.Camera -> ComposerStage.Recording
      MessageInputModeUi.Attachment -> ComposerStage.Attachment
    }

/** 免提录音状态 (P7 / P8) */
sealed interface TapRecordingState {
  object Idle : TapRecordingState

  data class Recording(val durationSec: Int) : TapRecordingState

  data class Paused(val durationSec: Int, val isPlayingPreview: Boolean = false) : TapRecordingState
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
    val stage: ComposerStage,
    val sendEnabled: Boolean,
    val contextActionState: ComposerInputModeContextActionUiState,
)

@Immutable
data class Composer2UiState(
    val inputMode: MessageInputModeUi,
    val bottomBarState: Composer2BottomBarUiState,
)

sealed interface ComposerAction {
  data class SelectInputMode(val mode: MessageInputModeUi) : ComposerAction

  data class SelectComposerStage(val stage: ComposerStage) : ComposerAction
}

sealed interface ComposerDefaultStageAction : ComposerAction {

  data class ChangeFocus(val focused: Boolean) : ComposerDefaultStageAction

  data object SendText : ComposerDefaultStageAction

  data class StartHoldToTalk(val rootOffset: Offset) : ComposerDefaultStageAction

  data class ReleaseHoldToTalk(val rootOffset: Offset) : ComposerDefaultStageAction

  data class MoveHoldToTalkTouch(val rootOffset: Offset) : ComposerDefaultStageAction
}

sealed interface ComposerVoiceRecordingAction : ComposerAction {
  data object StartVoiceRecording : ComposerVoiceRecordingAction

  data object PauseVoiceRecording : ComposerVoiceRecordingAction

  data object ResumeVoiceRecording : ComposerVoiceRecordingAction

  data object DiscardVoiceRecording : ComposerVoiceRecordingAction

  data object ToggleVoiceRecordingPreviewPlay : ComposerVoiceRecordingAction

  data object SendVoiceRecording : ComposerVoiceRecordingAction
}

sealed interface ComposerAttachmentStageAction : ComposerAction {
  data class SelectAlbums(val uris: List<android.net.Uri>) : ComposerAttachmentStageAction

  data class SelectAudios(val uris: List<android.net.Uri>) : ComposerAttachmentStageAction

  data class SelectFiles(val uris: List<android.net.Uri>) : ComposerAttachmentStageAction
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
