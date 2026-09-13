package top.fseasy.imlog.features.home.topiclog.composer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.graphicsLayer
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
  // Animate the solid circle appearing when ready to send
  val sendButtonScale by
      animateFloatAsState(
          targetValue = if (couldSendTextState) 1f else 0f,
          animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
          label = "SendButtonScale",
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
              .clickable(
                  role = Role.Button,
                  onClick = if (couldSendTextState) onSendClick else onAttachmentClick,
              ),
      contentAlignment = Alignment.Center,
  ) {
    // 1. Solid Primary background: only visible when sending
    if (sendButtonScale > 0.01f) {
      Box(
          modifier =
              Modifier.fillMaxSize()
                  .graphicsLayer {
                    scaleX = sendButtonScale
                    scaleY = sendButtonScale
                  }
                  .background(MaterialTheme.colorScheme.primary, CircleShape)
      )
    }

    // 2. Icon cross-fade with scale
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
        // Naked icon: use plain Add (no circle border) and scale up to 28dp
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = stringResource(R.string.composer_attachment_icon_desc),
            tint = iconTint,
            // Bigger to make visually balanced naked icon.
            modifier = Modifier.size(30.dp),
        )
      }
    }
  }
}
