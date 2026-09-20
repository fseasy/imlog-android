package top.fseasy.imlog.features.home.topiclog.timeline.contextmenu

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.fseasy.imlog.R
import top.fseasy.imlog.ui.components.BottomSheetTopBar
import top.fseasy.imlog.ui.components.contextmenu.TextSelectionActionBar
import top.fseasy.imlog.ui.components.contextmenu.DynamicHeightTextSelectionField
import top.fseasy.imlog.ui.components.contextmenu.rememberTextSelectionState
import top.fseasy.imlog.ui.theme.ImlogTheme

/**
 * Use a bottom sheet to response the text-selection request when Text is short Use fullscreen to
 * react when text is long. see
 * [top.fseasy.imlog.features.home.topiclog.fullscreencontainer.MessageTextSelection]
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
    MessageTextSelectionBottomSheetContent(
        text = text.trimEnd(),
        onDismiss = { dismissWithAnimation() },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageTextSelectionBottomSheetContent(
    text: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val selectionState = rememberTextSelectionState(text)

  Column(
      modifier = modifier.fillMaxWidth().navigationBarsPadding(),
      verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    BottomSheetTopBar(title = stringResource(R.string.term_select_text), onDismiss = onDismiss)

    DynamicHeightTextSelectionField(
        state = selectionState,
        modifier =
            Modifier.weight(1f, fill = false)
                .fillMaxWidth()
                .defaultMinSize(minHeight = 160.dp)
                .padding(horizontal = 16.dp),
    )

    TextSelectionActionBar(
        state = selectionState,
        onDismiss = onDismiss,
    )
  }
}

@Preview(
    name = "Message Text Selection Content - short text",
)
@Composable
private fun MessageTextSelectionContentShortTextPreviewBottomSheet() {
  ImlogTheme {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
      MessageTextSelectionBottomSheetContent(
          text = "This is a quick preview text message inside the bottom sheet.",
          onDismiss = {},
      )
    }
  }
}

@Preview(
    name = "Message Text Selection Content - Long text",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun MessageTextSelectionContentLongTextPreviewBottomSheet() {
  ImlogTheme() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
      MessageTextSelectionBottomSheetContent(
          text =
              """
              The provider determines how the context menu is shown and its appearance.
              The context menu can be customized by providing another implementation of this to LocalTextContextMenuDropdownProvider or LocalTextContextMenuToolbarProvider via a CompositionLocalProvider.
              If you want to modify the contents of the context menu, see Modifier.appendTextContextMenuComponents and Modifier.filterTextContextMenuComponents
              Crono, Lucca, and Marle get pushed through the Time Gate, and are transported to an unknown location. 
              After they go to the map, they find out that, that area was Bangor Dome. 
              """
                  .repeat(4)
                  .trimIndent(),
          onDismiss = {},
      )
    }
  }
}
