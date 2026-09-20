package top.fseasy.imlog.ui.components.contextmenu

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.contextmenu.modifier.filterTextContextMenuComponents
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.fseasy.imlog.R

/**
 * Text selection component for parent component with dynamic height:
 * - render selection color on background when all selected
 */
@Composable
fun DynamicHeightTextSelectionField(
    state: TextSelectionState,
    modifier: Modifier = Modifier,
) {
  val scrollState = rememberScrollState()

  val selectionBackgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
  val selectionColors =
      TextSelectionColors(
          handleColor = MaterialTheme.colorScheme.primary,
          backgroundColor =
              if (state.isAllSelected) Color.Transparent else selectionBackgroundColor,
      )

  Box(
      modifier =
          modifier
              .clip(RoundedCornerShape(16.dp))
              // If all selected, disable selection color and render the color of the full content.
              // Reason: full text selection will render a big range with rectangle radius, UGLY
              .background(if (state.isAllSelected) selectionBackgroundColor else Color.Transparent)
              .padding(16.dp)
              .verticalScroll(scrollState),
      contentAlignment = Alignment.CenterStart,
  ) {
    CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
      TextSelectionFieldComponent(textFieldValue = state.textFieldValue) { textFieldValue ->
        state.textFieldValue = textFieldValue
      }
    }
  }
}

/**
 * A variant text selection field used in the condition where parent available height is fixed.
 * e.g., full screen container with scaffold structure.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FillFixedHeightTextSelectionField(
    state: TextSelectionState,
    modifier: Modifier = Modifier,
) {
  val scrollState = rememberScrollState()
  val selectionColors =
      TextSelectionColors(
          handleColor = MaterialTheme.colorScheme.primary,
          backgroundColor = selectionBackgroundColor,
      )

  CompositionLocalProvider(
      LocalBringIntoViewSpec provides
          object : BringIntoViewSpec {
            // 当子组件（BasicTextField 聚焦或改变选区）要求滚动时，返回 0f（即绝对不偏移）
            override fun calculateScrollDistance(
                offset: Float,
                size: Float,
                containerSize: Float,
            ): Float = 0f
          }
  ) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(scrollState).padding(16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
      CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
        TextSelectionFieldComponent(
            textFieldValue = state.textFieldValue,
            onTextValueChange = { state.textFieldValue = it },
        )
      }
    }
  }
}

@Composable
private fun TextSelectionFieldComponent(
    textFieldValue: TextFieldValue,
    onTextValueChange: (TextFieldValue) -> Unit,
) {
  val focusRequester = remember { FocusRequester() }

  // To show the selection handler immediately
  LaunchedEffect(Unit) {
    onTextValueChange(textFieldValue.copy(selection = TextRange(0, textFieldValue.text.length)))
    focusRequester.requestFocus()
  }

  BasicTextField(
      value = textFieldValue,
      onValueChange = onTextValueChange,
      readOnly = true,
      textStyle =
          MaterialTheme.typography.bodyLarge.copy(
              color = MaterialTheme.colorScheme.onSurface,
              lineHeight = 28.sp,
              letterSpacing = 0.3.sp,
          ),
      cursorBrush = SolidColor(Color.Transparent),
      modifier =
          Modifier.fillMaxWidth()
              .filterTextContextMenuComponents { false }
              .focusRequester(focusRequester),
  )
}

private val selectionBackgroundColor
  @Composable get() = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)

@Composable
fun TextSelectionActionBar(
    state: TextSelectionState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Row(
      modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    TextButton(
        onClick = { state.toggleSelectAll() },
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
      Text(
          if (state.isAllSelected) stringResource(R.string.term_deselect)
          else stringResource(R.string.term_select_all),
          style = MaterialTheme.typography.labelLarge,
      )
    }

    Button(
        shape = CircleShape,
        enabled = state.hasSelection,
        onClick = { state.copySelection(onDismiss) },
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
