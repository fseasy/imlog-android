package top.fseasy.imlog.features.home.topiclog.composer2.tabbedcard

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import top.fseasy.imlog.features.home.topiclog.composer2.ComposerStage
import top.fseasy.imlog.features.home.topiclog.composer2.stageSelectorBarItems
import top.fseasy.imlog.ui.model.toImageVector
import top.fseasy.imlog.ui.theme.ImlogTheme

@Composable
fun InputModeToolBar(
  stage: ComposerStage,
  redDotStages: Set<ComposerStage>,
  onSelectStage: (ComposerStage) -> Unit,
  modifier: Modifier = Modifier,
) {
  val haptic = LocalHapticFeedback.current
  Surface(
      shape = CircleShape,
      color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
      modifier = modifier,
  ) {
    Row(
        modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      stageSelectorBarItems.forEach {
        val isSelected = stage == it.stage
        SegmentedToolbarItem(
            icon = it.iconVector.toImageVector(),
            isSelected = isSelected,
            showRedDots = it.stage in redDotStages && !isSelected,
            contentDescription = stringResource(it.contentDescriptionResId),
        ) {
          haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
          onSelectStage(it.stage)
        }
      }
    }
  }
}

@Composable
private fun SegmentedToolbarItem(
    icon: ImageVector,
    isSelected: Boolean,
    showRedDots: Boolean = false,
    contentDescription: String?,
    onClick: () -> Unit,
) {
  val containerColor = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent
  val contentColor =
      if (isSelected) MaterialTheme.colorScheme.primary
      else MaterialTheme.colorScheme.onSurfaceVariant

  Box(
      modifier =
          Modifier.size(width = 32.dp, height = 30.dp)
              .clip(CircleShape)
              .background(containerColor)
              .clickable(
                  interactionSource = remember { MutableInteractionSource() },
                  indication = null,
                  onClick = onClick,
              ),
      contentAlignment = Alignment.Center,
  ) {
    // 将 Icon 和红点放在同一个局部容器中
    Box {
      Icon(
          imageVector = icon,
          contentDescription = contentDescription,
          modifier = Modifier.size(18.dp),
          tint = contentColor,
      )

      if (showRedDots) {
        Box(
            modifier =
                Modifier.align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp) // 向外稍微偏移，视觉上贴合在图标右上角
                    .size(6.dp) // 红点大小
                    .background(MaterialTheme.colorScheme.error, CircleShape),
        )
      }
    }
  }
}

@Preview(name = "InputModeToolBar - Light", showBackground = true)
@Preview(
    name = "InputModeToolBar - Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun InputModeToolBarPreview() {
  ImlogTheme {
    Surface {
      Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        InputModeToolBar(
            ComposerStage.Default,
            redDotStages = setOf(ComposerStage.Default, ComposerStage.Attachment),
            onSelectStage = {},
        )
        InputModeToolBar(
            ComposerStage.Recording,
            redDotStages = setOf(ComposerStage.Default, ComposerStage.Attachment),
            onSelectStage = {},
        )
        InputModeToolBar(
            ComposerStage.Attachment,
            redDotStages = setOf(ComposerStage.Recording),
            onSelectStage = {},
        )
      }
    }
  }
}
