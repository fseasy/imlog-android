package top.fseasy.imlog.features.home.topiclog.fullscreencontainer

import android.content.ClipData
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.getSelectedText
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.fseasy.imlog.R
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.features.home.topiclog.LocalTopicLogSharedTransitionScope
import top.fseasy.imlog.features.home.topiclog.LocalTopicLogVisibilityScope
import top.fseasy.imlog.features.home.topiclog.toSharedTransitionElementId
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
fun MessageTextSelection(
    messageId: MessageId,
    text: String,
    onDismiss: () -> Unit,
    onForward: (selectedText: String) -> Unit,
    modifier: Modifier = Modifier,
) {
  val clipboard = LocalClipboard.current
  val scope = rememberCoroutineScope()
  val scrollState = rememberScrollState()

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
  val focusRequester = remember { FocusRequester() }

  val sharedTransitionScope = LocalTopicLogSharedTransitionScope.current
  val animatedVisibilityScope = LocalTopicLogVisibilityScope.current

  // Custom selection handle and highlight colors
  val selectionColors =
      TextSelectionColors(
          handleColor = MaterialTheme.colorScheme.primary,
          backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
      )

  // Bind sharedElement transition matching the origin bubble
  val sharedBoundsModifier =
      if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
          Modifier.sharedElement(
              rememberSharedContentState(key = toSharedTransitionElementId(messageId)),
              animatedVisibilityScope = animatedVisibilityScope,
          )
        }
      } else {
        Modifier
      }

  // Determine whether all text is currently selected
  val isAllSelected = textFieldValue.selection.length == text.length && text.isNotEmpty()

  // Returns currently highlighted text; falls back to full text if selection is collapsed
  fun getEffectiveText(): String {
    val selected = textFieldValue.getSelectedText().text
    return if (selected.isNotEmpty()) selected else text
  }

  Column(
      modifier = modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
  ) {
    // 1. Top Bar: Back button and title
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onDismiss) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = MaterialTheme.colorScheme.onBackground,
        )
      }
      Text(
          text = "Select Text",
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onBackground,
      )
    }

    // 2. Center Content: Scrollable text with native handles & magnifier
    Box(
        modifier =
            Modifier.weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
      CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
        BasicTextField(
            value = textFieldValue,
            onValueChange = { textFieldValue = it },
            readOnly = true,
            textStyle =
                MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                ),
            cursorBrush = SolidColor(Color.Transparent), // Hide cursor bar
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        )
      }
    }

    // 3. Bottom Action Bar: Select All (Toggle), Copy, Forward
    Surface(
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        // Action 1: Select All / Deselect (Smart Toggle)
        ActionButton(
            icon =
                if (isAllSelected) ImageVector.vectorResource(R.drawable.icon_stop)
                else ImageVector.vectorResource(R.drawable.icon_pause),
            label = if (isAllSelected) "Deselect" else "Select All",
            modifier = Modifier.weight(1f),
            onClick = {
              textFieldValue =
                  if (isAllSelected) {
                    textFieldValue.copy(selection = TextRange.Zero) // Clear handles
                  } else {
                    textFieldValue.copy(selection = TextRange(0, text.length)) // Select all
                  }
            },
        )

        // Action 2: Copy
        ActionButton(
            icon = ImageVector.vectorResource(R.drawable.icon_pause),
            label = "Copy",
            modifier = Modifier.weight(1f),
            onClick = {
              val targetText = getEffectiveText()
              scope.launch {
                clipboard.setClipEntry(
                    ClipEntry(ClipData.newPlainText("messageTextSelection", targetText))
                )
                // TODO: send message
                onDismiss()
              }
            },
        )

        // Action 3: Forward
        ActionButton(
            icon = ImageVector.vectorResource(R.drawable.icon_error),
            label = "Forward",
            modifier = Modifier.weight(1f),
            onClick = {
              val targetText = getEffectiveText()
              onForward(targetText)
              onDismiss()
            },
        )
      }
    }
  }

  // Request focus upon entry to reveal selection handles and bounds immediately
  LaunchedEffect(Unit) {
    focusRequester.requestFocus()
  }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  FilledTonalButton(
      onClick = onClick,
      shape = RoundedCornerShape(12.dp),
      modifier = modifier.height(44.dp),
  ) {
    Icon(
        imageVector = icon,
        contentDescription = label,
        modifier = Modifier.size(18.dp),
    )
    Spacer(Modifier.size(6.dp))
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
    )
  }
}

@Preview(name = "message text selection", showBackground = true, showSystemUi = true)
@Composable
private fun MessageTextSelectionPreview() {
  ImlogTheme {
    MessageTextSelection(
        messageId = MessageId.random(),
        text = "Here is a dummy short text",
        onDismiss = {},
        onForward = {},
    )
  }
}
