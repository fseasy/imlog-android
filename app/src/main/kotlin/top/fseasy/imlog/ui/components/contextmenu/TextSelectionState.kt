package top.fseasy.imlog.ui.components.contextmenu

import android.content.ClipData
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.getSelectedText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class TextSelectionState(
    initialText: String,
    val clipboard: Clipboard,
    val haptic: HapticFeedback,
    val coroutineScope: CoroutineScope,
) {
  var textFieldValue by
      mutableStateOf(
          TextFieldValue(
              text = initialText,
              selection = TextRange.Zero,
          )
      )

  val isAllSelected: Boolean
    get() =
        textFieldValue.selection.length == textFieldValue.text.length &&
            textFieldValue.text.isNotEmpty()

  val hasSelection: Boolean
    get() = textFieldValue.selection.length > 0

  fun toggleSelectAll() {
    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    textFieldValue =
        if (isAllSelected) {
          textFieldValue.copy(selection = TextRange.Zero)
        } else {
          textFieldValue.copy(selection = TextRange(0, textFieldValue.text.length))
        }
  }

  fun copySelection(onCopied: () -> Unit) {
    val selection = textFieldValue.getSelectedText().text
    coroutineScope.launch {
      clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("textSelection", selection)))
      haptic.performHapticFeedback(HapticFeedbackType.LongPress)
      onCopied()
    }
  }
}

/** State for TextSelection */
@Composable
fun rememberTextSelectionState(
    text: String,
    clipboard: Clipboard = LocalClipboard.current,
    haptic: HapticFeedback = LocalHapticFeedback.current,
    scope: CoroutineScope = rememberCoroutineScope(),
): TextSelectionState {
  return remember(text) {
    TextSelectionState(text, clipboard, haptic, scope)
  }
}
