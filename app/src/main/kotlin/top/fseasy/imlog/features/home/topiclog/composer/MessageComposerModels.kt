package top.fseasy.imlog.features.home.topiclog.composer

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import top.fseasy.imlog.domain.model.MessageInputMode
import top.fseasy.imlog.features.home.topiclog.timeline.QuotedMessageUiModel

/** Support parcelize */
@Parcelize
enum class MessageInputModeUiState : Parcelable {
  Text,
  Voice,
  Attachment,
}

fun MessageInputMode.toUiState(): MessageInputModeUiState =
    when (this) {
      MessageInputMode.Text -> MessageInputModeUiState.Text
      MessageInputMode.Voice -> MessageInputModeUiState.Voice
      MessageInputMode.Attachment -> MessageInputModeUiState.Attachment
    }

fun MessageInputModeUiState.toDomain(): MessageInputMode =
    when (this) {
      MessageInputModeUiState.Text -> MessageInputMode.Text
      MessageInputModeUiState.Voice -> MessageInputMode.Voice
      MessageInputModeUiState.Attachment -> MessageInputMode.Attachment
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
      val inputMode: MessageInputModeUiState?,
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
