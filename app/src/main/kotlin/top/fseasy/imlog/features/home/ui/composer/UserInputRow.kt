package top.fseasy.imlog.features.home.ui.composer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.fseasy.imlog.features.home.model.MessageInputModeParcelable


@Composable
fun UserInputRow(
    inputMode: MessageInputModeParcelable?,
    modifier: Modifier = Modifier,
) {

    AnimatedContent(
        targetState = inputMode,
        transitionSpec = {
            fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200))
        },
        label = "InputModeTransition"
    ) { mode ->
        when (mode) {
            null,
            MessageInputModeParcelable.Attachment,
                ->
                UserInputMenuRow()

            MessageInputModeParcelable.Text -> UserInputTextModeRow()
            MessageInputModeParcelable.Voice -> UserInputVoiceModeRow()
        }
    }

}
