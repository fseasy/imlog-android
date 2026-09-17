package top.fseasy.imlog.features.home.topiclog.timeline

import android.content.ClipData
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import kotlinx.coroutines.launch
import top.fseasy.imlog.R
import top.fseasy.imlog.ui.components.contextmenu.ContextMenuItem
import top.fseasy.imlog.ui.components.contextmenu.ContextMenuState
import top.fseasy.imlog.ui.components.contextmenu.VerticalContextMenu

/** @param contextMenuState define it in the parent component */
@Composable
fun MessageContextMenu(
    contextMenuState: ContextMenuState<AnyMessageUiModel>,
    onShowTextSelection: (MessageUiModel<MessageContentUiModel.Text>) -> Unit,
) {
  val clipboard = LocalClipboard.current
  val coroutineScope = rememberCoroutineScope()

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
                onClick = { onShowTextSelection(message.narrow()) },
            ),
        )
      },
  )
}
