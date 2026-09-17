package top.fseasy.imlog.features.home.topiclog.timeline

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.contextmenu.modifier.filterTextContextMenuComponents
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.getSelectedText
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import top.fseasy.imlog.R
import top.fseasy.imlog.ui.theme.ImlogTheme

/**
 * Lightweight text reader & selection modal bottom sheet for IM message bubbles.
 *
 * Uses read-only BasicTextField with EmptyTextToolbar to keep handles/magnifier functional while
 * handling all actions via bottom-pinned controls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageTextSelectionBottomSheet(
    text: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {

  val scope = rememberCoroutineScope()
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  // Helper function to play the slide-down exit animation before dismissing state
  fun dismissWithAnimation(onComplete: () -> Unit = onDismiss) {
    scope
        .launch {
          sheetState.hide() // Play slide-down animation
        }
        .invokeOnCompletion {
          if (!sheetState.isVisible) {
            onComplete()
          }
        }
  }

  ModalBottomSheet(
      onDismissRequest = onDismiss,
      sheetState = sheetState,
      dragHandle = { BottomSheetDefaults.DragHandle() },
      modifier = modifier,
  ) {
    MessageTextSelectionContent(
        text = text.trimEnd(),
        onDismiss = { dismissWithAnimation() },
    ) {
      BottomSheetTopBar { dismissWithAnimation() }
    }
  }
}

@Composable
private fun BottomSheetTopBar(
    onDismiss: () -> Unit,
) {
  Box(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
      contentAlignment = Alignment.Center,
  ) {
    Text(
        text = "选择文字",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurface,
    )

    IconButton(
        onClick = onDismiss,
        modifier = Modifier.align(Alignment.CenterEnd),
    ) {
      Icon(
          imageVector = Icons.Filled.Close,
          contentDescription = "关闭",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun MessageTextSelectionContent(
    text: String,
    onDismiss: () -> Unit,
    topBar: @Composable () -> Unit,
) {
  val haptic = LocalHapticFeedback.current
  val clipboard = LocalClipboard.current
  val scrollState = rememberScrollState()
  val focusRequester = remember { FocusRequester() }
  val scope = rememberCoroutineScope()

  // Track selection state. Defaults to selecting all text initially.
  var textFieldValue by
      remember(text) {
        mutableStateOf(
            TextFieldValue(
                text = text,
                selection = TextRange(0, text.length),
            )
        )
      }

  // Custom selection handle and highlight colors
  val selectionColors =
      TextSelectionColors(
          handleColor = MaterialTheme.colorScheme.primary,
          backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
      )

  val isAllSelected = textFieldValue.selection.length == text.length && text.isNotEmpty()
  val hasSelection = textFieldValue.selection.length > 0

  // Request focus upon presentation to expose selection handles immediately
  LaunchedEffect(Unit) {
    focusRequester.requestFocus()
  }

  Column(
      modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
  ) {
    topBar()
    // 2. Center Content: Scrollable text field with custom handles and suppressed toolbar
    // weight(1f, fill = false) allows the sheet to wrap short content without expanding fully
    Box(
        modifier =
            Modifier.weight(1f, fill = false)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 36.dp)
                .verticalScroll(scrollState),
    ) {
      CompositionLocalProvider(
          LocalTextSelectionColors provides selectionColors,
      ) {
        BasicTextField(
            value = textFieldValue,
            onValueChange = { textFieldValue = it },
            readOnly = true,
            textStyle =
                MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 28.sp,
                    letterSpacing = 0.3.sp,
                ),
            cursorBrush = SolidColor(Color.Transparent), // Hide caret indicator
            modifier =
                Modifier.fillMaxWidth()
                    .filterTextContextMenuComponents { false }
                    .focusRequester(focusRequester),
        )
      }
    }

    // 3. Bottom Action Bar: Select All / Deselect, Copy, Forward

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      TextButton(
          onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            textFieldValue =
                if (isAllSelected) {
                  textFieldValue.copy(selection = TextRange.Zero)
                } else {
                  textFieldValue.copy(selection = TextRange(0, text.length))
                }
          },
          shape = CircleShape,
          contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
      ) {
        Text(
            if (isAllSelected) stringResource(R.string.term_deselect)
            else stringResource(R.string.term_select_all),
            style = MaterialTheme.typography.labelLarge,
        )
      }

      Button(
          shape = CircleShape,
          enabled = hasSelection,
          onClick = {
            val selection = textFieldValue.getSelectedText().text
            scope.launch {
              clipboard.setClipEntry(
                  ClipEntry(ClipData.newPlainText("messageTextSelection", selection))
              )
              haptic.performHapticFeedback(HapticFeedbackType.LongPress)
              onDismiss()
            }
          },
          contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
      ) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.icon_content_copy),
            contentDescription = stringResource(R.string.term_copy),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(6.dp))
        Text(
            stringResource(R.string.term_copy),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        )
      }
    }
  }
}

@Preview(name = "Message Text Selection Bottom Sheet", showBackground = true, showSystemUi = true)
@Composable
private fun MessageTextSelectionContentPreview() {
  ImlogTheme {
    Box(modifier = Modifier.fillMaxSize()) {
      MessageTextSelectionContent(
          text = "This is a quick preview text message inside the bottom sheet.",
          onDismiss = {},
      ) {
        BottomSheetTopBar {}
      }
    }
  }
}
