package top.fseasy.imlog.features.home.topiclog.composer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import top.fseasy.imlog.R

@Composable
fun RightActionSlot(
    couldSendTextState: Boolean,
    onSendClick: () -> Unit,
    onAttachmentClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  // 1. Animate container background and icon tint smoothly
  val backgroundColor by
      animateColorAsState(
          targetValue =
              if (couldSendTextState) {
                MaterialTheme.colorScheme.primary
              } else {
                Color.Transparent
              },
          label = "ButtonBackgroundColor",
      )

  val iconTint by
      animateColorAsState(
          targetValue =
              if (couldSendTextState) {
                MaterialTheme.colorScheme.onPrimary
              } else {
                MaterialTheme.colorScheme.onSurfaceVariant
              },
          label = "ButtonIconTint",
      )

  Box(
      modifier =
          modifier
              .size(ComposerMinActionHeight)
              .clip(CircleShape)
              .background(backgroundColor)
              .clickable(
                  role = Role.Button,
                  onClick = if (couldSendTextState) onSendClick else onAttachmentClick,
              ),
      contentAlignment = Alignment.Center,
  ) {
    AnimatedContent(
        targetState = couldSendTextState,
        transitionSpec = {
          (scaleIn(initialScale = 0.5f) + fadeIn()) togetherWith
              (scaleOut(targetScale = 0.5f) + fadeOut())
        },
        label = "RightSlotIconTransition",
    ) { couldSendText ->
      if (couldSendText) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Send,
            contentDescription = stringResource(R.string.composer_send_btn_desc),
            tint = iconTint,
            modifier = Modifier.size(ComposerIconSize),
        )
      } else {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = stringResource(R.string.composer_attachment_icon_desc),
            tint = iconTint,
            modifier = Modifier.size(ComposerIconSize),
        )
      }
    }
  }
}
