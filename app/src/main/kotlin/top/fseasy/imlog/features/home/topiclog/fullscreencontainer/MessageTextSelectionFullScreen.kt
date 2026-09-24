package top.fseasy.imlog.features.home.topiclog.fullscreencontainer

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import top.fseasy.imlog.R
import top.fseasy.imlog.ui.components.contextmenu.FillFixedHeightTextSelectionField
import top.fseasy.imlog.ui.components.contextmenu.TextSelectionActionBar
import top.fseasy.imlog.ui.components.contextmenu.rememberTextSelectionState
import top.fseasy.imlog.ui.theme.ImlogTheme

/**
 * Fullscreen text reader & selection viewer.
 *
 * Built on read-only BasicTextField to ensure:
 * 1. Scrolling does NOT deselect text or fight with list recycling.
 * 2. Dragging selection handles near edge triggers auto-scrolling.
 * 3. Bottom actions dynamically read the selected substring.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MessageTextSelectionFullScreen(
  text: String,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  MessageTextSelectionFullScreenContent(
      text = text.trimEnd(),
      onDismiss = onDismissRequest,
      modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageTextSelectionFullScreenContent(
    text: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {

  val selectionState = rememberTextSelectionState(text)
  val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

  Scaffold(
      modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
      topBar = {
        CenterAlignedTopAppBar(
            title = {
              Text(
                  text = stringResource(R.string.term_select_text),
                  style = MaterialTheme.typography.titleMedium,
              )
            },
            navigationIcon = {
              IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.term_close),
                )
              }
            },
            scrollBehavior = scrollBehavior,
        )
      },
      bottomBar = {
        TextSelectionActionBar(
            state = selectionState,
            onDismiss = onDismiss,
            modifier = Modifier.navigationBarsPadding(),
        )
      },
  ) { innerPadding ->
    FillFixedHeightTextSelectionField(
        state = selectionState,
        modifier = Modifier.fillMaxSize().padding(innerPadding),
    )
  }
}

@Preview(name = "message text selection", showBackground = true, showSystemUi = true)
@Composable
private fun MessageTextSelectionPreview() {
  ImlogTheme {
    MessageTextSelectionFullScreen(
        text = "Here is a dummy short text",
        onDismissRequest = {},
    )
  }
}
