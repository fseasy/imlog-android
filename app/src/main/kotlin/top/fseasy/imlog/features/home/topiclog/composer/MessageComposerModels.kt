package top.fseasy.imlog.features.home.topiclog.composer

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import top.fseasy.imlog.domain.model.MessageInputMode
import top.fseasy.imlog.features.home.topiclog.timeline.QuotedMessageUiModel

/** Support parcelize */
@Parcelize
enum class MessageInputModeUi : Parcelable {
  Text,
  HoldToRecord,
  VoiceNote,
  Attachment,
  Camera,
}

fun MessageInputMode.toUiState(): MessageInputModeUi =
    when (this) {
      MessageInputMode.Text -> MessageInputModeUi.Text
      MessageInputMode.HoldToRecord -> MessageInputModeUi.HoldToRecord
      MessageInputMode.VoiceNote -> MessageInputModeUi.VoiceNote
      MessageInputMode.Attachment -> MessageInputModeUi.Attachment
      MessageInputMode.Camera -> MessageInputModeUi.Camera
    }

fun MessageInputModeUi.toDomain(): MessageInputMode =
    when (this) {
      MessageInputModeUi.Text -> MessageInputMode.Text
      MessageInputModeUi.HoldToRecord -> MessageInputMode.HoldToRecord
      MessageInputModeUi.VoiceNote -> MessageInputMode.VoiceNote
      MessageInputModeUi.Attachment -> MessageInputMode.Attachment
      MessageInputModeUi.Camera -> MessageInputMode.Camera
    }

/**
 * What's Meta: includes all the attributes of drafts except the inputText. As it's high frequent
 * fresh data, will be processed alone.
 *
 * Support Parcelize
 */
@Parcelize
sealed interface ComposerDraftMeta : Parcelable {
  @Parcelize data object Loading : ComposerDraftMeta

  @Parcelize
  data class Ready(
      val inputMode: MessageInputModeUi?,
      val quotedMessage: QuotedMessageUiModel?,
  ) : ComposerDraftMeta
}

sealed interface ComposerUiEffect {
  //    object HideKeyboard : ComposerUiEffect
  object PopBackStack : ComposerUiEffect

  object Vibrate : ComposerUiEffect
}

enum class VoiceButtonState {
  Capsule,
  Circle,
  Hidden,
}

/** Action group for input-mode trigger */
class InputModeSetActions(
    val onTextInputFocusChange: (Boolean) -> Unit,
    val onVoiceInputSingleClick: () -> Unit,
    val onAttachmentClick: () -> Unit,
    val onTextModeImeHide: () -> Unit,
)
