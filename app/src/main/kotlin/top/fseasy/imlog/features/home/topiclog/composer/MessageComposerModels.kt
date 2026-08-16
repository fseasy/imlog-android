package top.fseasy.imlog.features.home.topiclog.composer

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import top.fseasy.imlog.domain.model.MessageInputMode
import top.fseasy.imlog.features.home.topiclog.timeline.QuotedMessageUiModel

@Parcelize
enum class MessageInputModeParcelable : Parcelable {
  Text,
  Voice,
  Attachment,
}

fun MessageInputMode.toParcelable(): MessageInputModeParcelable =
    when (this) {
      MessageInputMode.Text -> MessageInputModeParcelable.Text
      MessageInputMode.Voice -> MessageInputModeParcelable.Voice
      MessageInputMode.Attachment -> MessageInputModeParcelable.Attachment
    }

fun MessageInputModeParcelable.toDomain(): MessageInputMode =
    when (this) {
      MessageInputModeParcelable.Text -> MessageInputMode.Text
      MessageInputModeParcelable.Voice -> MessageInputMode.Voice
      MessageInputModeParcelable.Attachment -> MessageInputMode.Attachment
    }

/**
 * Will be saved to SavedStateHandle, needs the parcelize interface. What's Meta: includes all the
 * attributes of drafts except the inputText. As it's high frequent fresh data, will be processed
 * alone.
 */
@Parcelize
sealed interface ComposerDraftMeta : Parcelable {
  @Parcelize data object Loading : ComposerDraftMeta

  @Parcelize
  data class Ready(
      val inputMode: MessageInputModeParcelable?,
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
)
