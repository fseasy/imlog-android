package top.fseasy.imlog.features.home.topiclog.composer

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

/** default Row (Left: TextField+VoiceButton | Right:Attachment/SendButton 1:1 slot) */
@Composable
fun UserInputDefaultRow(
    textFieldValue: TextFieldValue,
    isTextMode: Boolean,
    inputModeSetActions: InputModeSetActions,
    onTextChanged: (TextFieldValue) -> Unit,
    onSendText: () -> Unit,
) {
  val hasText = textFieldValue.text.isNotEmpty()
  val voiceButtonState =
      if (isTextMode) {
        if (hasText) VoiceButtonState.Hidden else VoiceButtonState.Circle
      } else {
        if (hasText) VoiceButtonState.Circle else VoiceButtonState.Capsule
      }
  // NOTE: here we show send button only when it's in text mode.
  //       it's different from WeChat, which show it just when `hasText`
  //       WHY: I think it's better to show attachment button when not-in text mode
  //            I don't think it's necessary to require user clean text before sending attachment
  val couldSendingTextState = isTextMode && hasText

  Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.Bottom, // bottom alignment to allow text grow up
  ) {
    TextAndVoiceComponent(
        textFieldValue = textFieldValue,
        voiceButtonState = voiceButtonState,
        onTextChanged = onTextChanged,
        onFocusChanged = inputModeSetActions.onTextInputFocusChange,
        onVoiceSingleClick = inputModeSetActions.onVoiceInputSingleClick,
        modifier = Modifier.weight(1f),
    )

    Spacer(modifier = Modifier.width(8.dp))

    RightActionSlot(
        couldSendingTextState = couldSendingTextState,
        onSendClick = onSendText,
        onAttachmentClick = inputModeSetActions.onAttachmentClick,
    )
  }
}
