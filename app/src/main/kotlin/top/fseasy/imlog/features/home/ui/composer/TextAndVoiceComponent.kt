package top.fseasy.imlog.features.home.ui.composer

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
import androidx.compose.ui.unit.sp
import timber.log.Timber
import top.fseasy.imlog.R
import top.fseasy.imlog.features.home.model.VoiceButtonState

/**
 * Input + Voice Component
 */
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
        modifier = modifier, verticalAlignment = Alignment.CenterVertically
    ) {
        UserInputTextField(
            textFieldValue = textFieldValue,
            onTextChanged = onTextChanged,
            onFocusChanged = onFocusChanged,
            modifier = Modifier.weight(1f)
        )
        AnimatedContent(
            targetState = voiceButtonState, transitionSpec = {
                val fadeInOut = fadeIn(animationSpec = tween(200)) togetherWith fadeOut(
                    animationSpec = tween(200)
                )
                fadeInOut.using(
                    SizeTransform(clip = false)
                )
            }, label = "VoiceButtonTransition"
        ) { state ->
            when (state) {
                VoiceButtonState.Capsule -> {
                    Row {
                        Spacer(modifier = Modifier.width(8.dp))
                        UserInputVoiceButton(onClick = onVoiceSingleClick, isCircle = false)
                    }
                }

                VoiceButtonState.Circle -> {
                    Row {
                        Spacer(modifier = Modifier.width(8.dp))
                        UserInputVoiceButton(onClick = onVoiceSingleClick, isCircle = true)
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

    val shape = RoundedCornerShape(12.dp)
    val backgroundColor = if (isFocused) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val borderColor = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent

    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .border(1.dp, borderColor, shape)
            .padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = textFieldValue,
            onValueChange = onTextChanged,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 22.dp, max = 100.dp)
                .onFocusChanged { state ->
                    isFocused = state.isFocused
                    onFocusChanged(state.isFocused)
                },
            textStyle = TextStyle(
                fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
        ) { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart
            ) {

                if (textFieldValue.text.isEmpty()) {
                    Text(
                        text = stringResource(R.string.composer_text_input_placeholder),
                        color = Color.Gray
                    )
                }

                innerTextField()
            }
        }
    }
}

@Composable
private fun UserInputVoiceButton(
    onClick: () -> Unit,
    isCircle: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = CircleShape, // Always use CircleShape, depending on Padding to change shape
        color = MaterialTheme.colorScheme.primaryContainer, modifier = modifier.pointerInput(Unit) {
            detectTapGestures(
                onTap = { onClick() },
                onLongPress = { Timber.d("Long press. SKIP NOW") })
        }) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (isCircle) 10.dp else 16.dp, vertical = 10.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.icon_mic),
                contentDescription = stringResource(R.string.composer_mic_icon_desc),
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp)
            )

            if (!isCircle) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.composer_voice_button_text),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}