package top.fseasy.imlog.features.home.topiclog.composer

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import top.fseasy.imlog.ui.theme.ImlogTheme

/** default Row (Left: TextField+VoiceButton | Right:Attachment/SendButton 1:1 slot) */
@Composable
fun UserInputDefaultRow(
    textFieldValue: TextFieldValue,
    isTextMode: Boolean,
    inputModeSetActions: InputModeSetActions,
    onTextChanged: (TextFieldValue) -> Unit,
    onSendText: () -> Unit,
    modifier: Modifier = Modifier,
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
      modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.Bottom, // bottom alignment to allow text grow up
      horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    TextAndVoiceComponent(
        textFieldValue = textFieldValue,
        voiceButtonState = voiceButtonState,
        onTextChanged = onTextChanged,
        onFocusChanged = inputModeSetActions.onTextInputFocusChange,
        onVoiceSingleClick = inputModeSetActions.onVoiceInputSingleClick,
        modifier = Modifier.weight(1f),
    )

    RightActionSlot(
        couldSendTextState = couldSendingTextState,
        onSendClick = onSendText,
        onAttachmentClick = inputModeSetActions.onAttachmentClick,
    )
  }
}

// For Preview Helper
@Composable
private fun rememberDummyInputModeSetActions(): InputModeSetActions {
  return remember {
    InputModeSetActions(
        onTextInputFocusChange = {},
        onVoiceInputSingleClick = {},
        onAttachmentClick = {},
        onTextModeImeHide = {},
    )
  }
}

@Preview(name = "1. Empty State - Light", showBackground = true)
@Preview(
    name = "1. Empty State - Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun UserInputDefaultRowEmptyPreview() {
  ImlogTheme {
    Surface {
      var textState by remember { mutableStateOf(TextFieldValue("")) }

      UserInputDefaultRow(
          textFieldValue = textState,
          isTextMode = false,
          inputModeSetActions = rememberDummyInputModeSetActions(),
          onTextChanged = { textState = it },
          onSendText = {},
      )
    }
  }
}

@Preview(name = "2. Has Text State (Send Mode)", showBackground = true)
@Composable
private fun UserInputDefaultRowHasTextPreview() {
  ImlogTheme {
    Surface {
      var textState by remember {
        mutableStateOf(TextFieldValue("Hello Compose!"))
      }

      UserInputDefaultRow(
          textFieldValue = textState,
          isTextMode = true,
          inputModeSetActions = rememberDummyInputModeSetActions(),
          onTextChanged = { textState = it },
          onSendText = { textState = TextFieldValue("") },
      )
    }
  }
}

@Preview(name = "3. Multiline Expand State", showBackground = true)
@Composable
private fun UserInputDefaultRowMultilinePreview() {
  ImlogTheme {
    Surface {
      var textState by remember {
        mutableStateOf(TextFieldValue("这是一段很长的测试文字\nChange Line 1\nAnother Line 2\n测试多行向上生长效果"))
      }

      UserInputDefaultRow(
          textFieldValue = textState,
          isTextMode = true,
          inputModeSetActions = rememberDummyInputModeSetActions(),
          onTextChanged = { textState = it },
          onSendText = { textState = TextFieldValue("") },
      )
    }
  }
}
