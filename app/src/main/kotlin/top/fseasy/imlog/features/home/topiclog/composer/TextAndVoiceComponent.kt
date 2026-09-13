package top.fseasy.imlog.features.home.topiclog.composer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import top.fseasy.imlog.R

/** Input + Voice Component */
@Composable
fun TextAndVoiceComponent(
    textFieldValue: TextFieldValue,
    voiceButtonState: VoiceButtonState,
    onTextChanged: (TextFieldValue) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onVoiceSingleClick: () -> Unit,
    modifier: Modifier = Modifier,
) {

  Row(
      modifier = modifier,
      verticalAlignment = Alignment.CenterVertically,
  ) {
    UserInputTextField(
        textFieldValue = textFieldValue,
        onTextChanged = onTextChanged,
        onFocusChanged = onFocusChanged,
        modifier = Modifier.weight(1f),
    )
    AnimatedContent(
        targetState = voiceButtonState,
        transitionSpec = {
          val fadeInOut =
              fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200))
          fadeInOut.using(SizeTransform(clip = false))
        },
        label = "VoiceButtonTransition",
    ) { state ->
      when (state) {
        VoiceButtonState.Capsule -> {
          Row {
            Spacer(modifier = Modifier.width(8.dp))
            UserInputVoiceCapsuleButton(onClick = onVoiceSingleClick, onLongPress = {})
          }
        }

        VoiceButtonState.Circle -> {
          Row {
            Spacer(modifier = Modifier.width(8.dp))
            UserInputVoiceCircleButton(onClick = onVoiceSingleClick, onLongPress = {})
          }
        }

        VoiceButtonState.Hidden -> {
          Spacer(modifier = Modifier.size(0.dp))
        }
      }
    }
  }
}

@Composable
private fun UserInputTextField(
    textFieldValue: TextFieldValue,
    onTextChanged: (TextFieldValue) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
  var isFocused by remember { mutableStateOf(false) }

  // Bind focusRequester on this BasicTextField element, so it can process the ime show/hide in top
  // element
  val focusRequester = LocalComposerFocusRequester.current
  val focusRequesterModifier =
      if (focusRequester != null) {
        Modifier.focusRequester(focusRequester)
      } else {
        Modifier
      }

  val shape = RoundedCornerShape(InputMethodRoundRadius)
  val backgroundColor = MaterialTheme.colorScheme.surface

  val borderColor =
      if (isFocused) MaterialTheme.colorScheme.primary
      else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)

  Box(
      modifier =
          modifier
              // Align the min height of the composer
              .defaultMinSize(minHeight = ComposerMinActionHeight)
              .clip(shape)
              .background(backgroundColor)
              .border(1.dp, borderColor, shape)
              .padding(horizontal = 14.dp, vertical = InputMethodVerticalPadding),
      contentAlignment = Alignment.CenterStart,
  ) {
    BasicTextField(
        value = textFieldValue,
        onValueChange = onTextChanged,
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(max = 100.dp)
                .onFocusChanged { state ->
                  isFocused = state.isFocused
                  onFocusChanged(state.isFocused)
                }
                .then(focusRequesterModifier),
        textStyle =
            TextStyle(
                fontSize = ComposerFontSize,
                color = MaterialTheme.colorScheme.onSurface,
            ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
    ) { innerTextField ->
      Box(
          modifier = Modifier.fillMaxWidth(),
          contentAlignment = Alignment.CenterStart,
      ) {
        if (textFieldValue.text.isEmpty()) {
          Text(
              text = stringResource(R.string.composer_text_input_placeholder),
              color = Color.Gray,
              fontSize = ComposerFontSize,
          )
        }

        innerTextField()
      }
    }
  }
}

@Composable
fun UserInputVoiceCapsuleButton(
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Surface(
      shape = RoundedCornerShape(InputMethodRoundRadius),
      color = MaterialTheme.colorScheme.surfaceVariant,
      contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
      shadowElevation = 0.dp,
      modifier =
          modifier.defaultMinSize(minHeight = ComposerMinActionHeight).pointerInput(Unit) {
            detectTapGestures(
                onTap = { onClick() },
                onLongPress = { onLongPress() },
            )
          },
  ) {
    Row(
        modifier =
            Modifier.padding(
                horizontal = 14.dp,
                vertical = InputMethodVerticalPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
      Icon(
          imageVector = ImageVector.vectorResource(R.drawable.icon_mic),
          contentDescription = stringResource(R.string.composer_mic_icon_desc),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(ComposerIconSize),
      )

      Spacer(modifier = Modifier.width(6.dp))

      Text(
          text = stringResource(R.string.composer_voice_button_text),
          style = MaterialTheme.typography.labelLarge,
          fontSize = ComposerFontSize,
      )
    }
  }
}

/** Only a mic icon */
@Composable
fun UserInputVoiceCircleButton(
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Surface(
      shape = CircleShape,
      color = MaterialTheme.colorScheme.surfaceVariant,
      contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
      shadowElevation = 0.dp,
      modifier =
          modifier.size(ComposerMinActionHeight).pointerInput(Unit) {
            detectTapGestures(
                onTap = { onClick() },
                onLongPress = { onLongPress() },
            )
          },
  ) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize(),
    ) {
      Icon(
          imageVector = ImageVector.vectorResource(R.drawable.icon_mic),
          contentDescription = stringResource(R.string.composer_mic_icon_desc),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(ComposerIconSize),
      )
    }
  }
}

private val InputMethodVerticalPadding = 10.dp
private val InputMethodRoundRadius = 20.dp
