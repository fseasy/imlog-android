package top.fseasy.imlog.features.home.topiclog.composer2

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TextStage(
    text: String,
    focusRequester: FocusRequester,
    onTextChange: (String) -> Unit,
    onHoldVoiceStart: () -> Unit,
) {
  var isFocused by remember { mutableStateOf(false) }

  Box(
      modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
      contentAlignment = Alignment.TopStart,
  ) {
    if (text.isEmpty()) {
      Text(
          text = "Tap to text or Hold to Speak",
          color = Color(0xFF8E8E93),
          fontSize = 17.sp,
          modifier = Modifier.padding(top = 2.dp),
      )
    }

    BasicTextField(
        value = text,
        onValueChange = onTextChange,
        textStyle = TextStyle(color = Color.Black, fontSize = 17.sp),
        cursorBrush = SolidColor(Color.Black),
        modifier =
            Modifier.fillMaxWidth().focusRequester(focusRequester).onFocusChanged {
              isFocused = it.isFocused
            },
    )

    // 仅在“未聚焦且无文字”时拦截手势：长按直达语音对讲（释放手指压力），单击唤起软键盘
    if (!isFocused && text.isEmpty()) {
      Box(
          modifier =
              Modifier.matchParentSize().pointerInput(Unit) {
                detectTapGestures(
                    onTap = { focusRequester.requestFocus() },
                    onLongPress = { onHoldVoiceStart() },
                )
              }
      )
    }
  }
}
