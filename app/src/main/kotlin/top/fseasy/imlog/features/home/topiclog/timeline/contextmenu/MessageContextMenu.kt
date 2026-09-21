package top.fseasy.imlog.features.home.topiclog.timeline.contextmenu

import android.content.ClipData
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.fseasy.imlog.R
import top.fseasy.imlog.features.home.topiclog.timeline.AnyMessageUiModel
import top.fseasy.imlog.features.home.topiclog.timeline.MessageContentUiModel
import top.fseasy.imlog.features.home.topiclog.timeline.MessageUiModel
import top.fseasy.imlog.features.home.topiclog.timeline.messagebubble.BUBBLE_MAX_WIDTH_IN_DP
import top.fseasy.imlog.features.home.topiclog.timeline.messagebubble.TextMessageTypograph
import top.fseasy.imlog.features.home.topiclog.timeline.narrow
import top.fseasy.imlog.ui.components.contextmenu.ContextMenuItem
import top.fseasy.imlog.ui.components.contextmenu.ContextMenuState
import top.fseasy.imlog.ui.components.contextmenu.VerticalContextMenu
import top.fseasy.imlog.ui.util.rememberPx

/** @param contextMenuState define it in the parent component */
@Composable
fun MessageContextMenu(
    contextMenuState: ContextMenuState<AnyMessageUiModel>,
    onShowFullScreenTextSelection: (MessageUiModel<MessageContentUiModel.Text>) -> Unit,
) {
  val clipboard = LocalClipboard.current
  val coroutineScope = rememberCoroutineScope()
  var textSelectionBottomSheetPayload by rememberSaveable { mutableStateOf<String?>(null) }

  val textMessageBubbleLineCalculator = rememberTextMessageLineCalculator()

  VerticalContextMenu(
      contextMenuState,
      items = { message ->
        listOf(
            ContextMenuItem(
                title = stringResource(R.string.term_copy),
                iconVector = ImageVector.vectorResource(R.drawable.icon_content_copy),
                isDestructive = false,
                isApplicable = { message.content is MessageContentUiModel.Text },
                onClick = {
                  val fullText =
                      message.content as? MessageContentUiModel.Text ?: return@ContextMenuItem
                  coroutineScope.launch {
                    clipboard.setClipEntry(
                        ClipEntry(ClipData.newPlainText("copyMessageFullText", fullText.text))
                    )
                  }
                },
            ),
            ContextMenuItem(
                title = stringResource(R.string.term_select_text),
                iconVector = ImageVector.vectorResource(R.drawable.icon_text_select_start),
                isDestructive = false,
                isApplicable = { message.content is MessageContentUiModel.Text },
                onClick = {
                  val fullText =
                      message.content as? MessageContentUiModel.Text ?: return@ContextMenuItem
                  val lineCount = textMessageBubbleLineCalculator(fullText.text)
                  if (lineCount > MIN_LINES_FOR_FULL_SCREEN_TEXT_SELECTION) {
                    onShowFullScreenTextSelection(message.narrow())
                  } else {
                    textSelectionBottomSheetPayload = fullText.text
                  }
                },
            ),
        )
      },
  )

  // Text-Selection Bottom Sheet (for Context Menu action)
  // -- Assign to `val` to enable smart cast
  val currentTextSelectionBottomSheetPayload = textSelectionBottomSheetPayload
  if (!currentTextSelectionBottomSheetPayload.isNullOrEmpty()) {
    MessageTextSelectionBottomSheet(
        text = currentTextSelectionBottomSheetPayload,
        onDismiss = { textSelectionBottomSheetPayload = null },
    )
  }
}

/**
 * Measure text lines that rendered in message text bubble. Used to decide how to render
 * text-selection: bottom-sheet or fullscreen
 *
 * We previously want to directly get the bubble height, but it needs more changes. This way is more
 * decoupled and should just work.
 */
@Composable
fun rememberTextMessageLineCalculator(): (String) -> Int {
  val textMeasurer = rememberTextMeasurer()
  val maxWidthPx = BUBBLE_MAX_WIDTH_IN_DP.dp.rememberPx()
  val style = TextMessageTypograph

  return remember(textMeasurer, maxWidthPx, style) {
    { text: String ->
      textMeasurer
          .measure(
              text = AnnotatedString(text),
              style = style,
              constraints = Constraints(maxWidth = maxWidthPx),
          )
          .lineCount
    }
  }
}

private const val MIN_LINES_FOR_FULL_SCREEN_TEXT_SELECTION = 10
