package top.fseasy.imlog.features.home.topiclog.composer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.flow.StateFlow

@Composable
fun UserInputRow(
    inputText: String,
    inputMode: MessageInputModeParcelable?,
    voiceRecordingUiStateFlow: StateFlow<VoiceRecordingUiState>,
    inputModeSetActions: InputModeSetActions,
    onInputTextChange: (String) -> Unit,
    onSendText: () -> Unit,
    onSendVoice: () -> Unit,
    onCancelVoiceRecoding: () -> Unit,
    modifier: Modifier = Modifier,
) {
  // Put textFieldValueState on parent component, so it's state can be saved even
  // it switched to Voice input.
  var textFieldValueState by remember {
    mutableStateOf(TextFieldValue(text = inputText))
  }

  LaunchedEffect(inputText) {
    // For condition 1. InputText load from draft db. 2. InputText cleared after sending message
    if (inputText != textFieldValueState.text) {
      textFieldValueState =
          textFieldValueState.copy(
              text = inputText,
              // set cursor pointer to the end
              selection = TextRange(inputText.length),
          )
    }
  }

  val isTextMode = inputMode == MessageInputModeParcelable.Text

  AnimatedContent(
      targetState = inputMode,
      transitionSpec = {
        fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200))
      },
      label = "InputModeTransition",
  ) { mode ->
    when (mode) {
      MessageInputModeParcelable.Voice ->
          VoiceRecodingRow(
              voiceRecordingUiStateFlow = voiceRecordingUiStateFlow,
              onSend = onSendVoice,
              onCancel = onCancelVoiceRecoding,
              modifier = modifier,
          )

      else ->
          UserInputDefaultRow(
              textFieldValue = textFieldValueState,
              isTextMode = isTextMode,
              inputModeSetActions = inputModeSetActions,
              onTextChanged = { newValue ->
                textFieldValueState = newValue
                if (newValue.text != inputText) { // filter unnecessary call
                  onInputTextChange(newValue.text)
                }
              },
              onSendText = onSendText,
          )
    }
  }
}
