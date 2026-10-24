package top.fseasy.imlog.features.home.topiclog.composer2.classic

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import timber.log.Timber
import top.fseasy.imlog.R
import top.fseasy.imlog.features.home.topiclog.composer.LocalComposerFocusRequester
import top.fseasy.imlog.features.home.topiclog.composer2.tapOrHoldToRecordGesture
import top.fseasy.imlog.ui.theme.composerStyle

@Composable
internal fun ComposerBar(
    textFieldState: TextFieldState,
    onAttachmentClick: () -> Unit,
    onSendText: () -> Unit,
    onTextFieldFocusChanged: (focused: Boolean) -> Unit,
    onHoldToRecordAction: (ComposerHoldToRecordAction) -> Unit,
) {
  var isFocused by remember { mutableStateOf(false) }

  // Bind focusRequester on this BasicTextField element, so it can process the ime show/hide in top
  // element
  val focusRequester = LocalComposerFocusRequester.current ?: remember { FocusRequester() }
  val keyboardController = LocalSoftwareKeyboardController.current

  Row(
      modifier =
          Modifier.fillMaxWidth()
              .heightIn(min = 52.dp, max = 120.dp) // 保证高度饱满大气
              .padding(vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
        modifier = Modifier.weight(1f).padding(start = 18.dp, end = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
      BasicTextField(
          state = textFieldState,
          textStyle = MaterialTheme.typography.composerStyle,
          cursorBrush = SolidColor(Color.Black),
          modifier =
              Modifier.fillMaxWidth().focusRequester(focusRequester).onFocusChanged {
                isFocused = it.isFocused
                onTextFieldFocusChanged(it.isFocused)
              },
          decorator = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
              if (textFieldState.text.isEmpty()) {
                val hintResId =
                    if (!isFocused) R.string.composer_default_stage_input_placeholder
                    else R.string.composer_text_input_placeholder
                Text(
                    text = stringResource(hintResId),
                    color = Color.Gray,
                    style = MaterialTheme.typography.composerStyle,
                )
              }
              innerTextField()
            }
          },
      )

      // 仅在“未聚焦且无文字”时拦截手势：长按直达语音对讲，单击唤起软键盘
      if (!isFocused && textFieldState.text.isEmpty()) {
        Timber.i("Render gesture box done")
        Box(
            modifier =
                Modifier.matchParentSize()
                    .tapOrHoldToRecordGesture(
                        onTap = {
                          focusRequester.requestFocus()
                          keyboardController?.show()
                        },
                        onHoldStart = { rootOffset ->
                          onHoldToRecordAction(
                              ComposerHoldToRecordAction.StartHoldToTalk(rootOffset)
                          )
                        },
                        onMove = { rootOffset ->
                          onHoldToRecordAction(
                              ComposerHoldToRecordAction.MoveHoldToTalkTouch(rootOffset)
                          )
                        },
                        onRelease = { rootOffset ->
                          onHoldToRecordAction(
                              ComposerHoldToRecordAction.ReleaseHoldToTalk(rootOffset)
                          )
                        },
                    )
        )
      }
    }
    IconButton(onClick = onAttachmentClick) {
      Icon(painterResource(R.drawable.icon_mic), contentDescription = "")
    }

    IconButton(onClick = onSendText) {
      Icon(painterResource(R.drawable.icon_arrow_upward_alt_48px), contentDescription = "")
    }
  }
}
