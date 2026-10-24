package top.fseasy.imlog.features.home.topiclog.composer2.tabbedcard

import android.content.res.Configuration
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import top.fseasy.imlog.R
import top.fseasy.imlog.features.home.topiclog.composer2.ComposerBottomContextAction
import top.fseasy.imlog.features.home.topiclog.composer2.ComposerInputModeContextActionUiState
import top.fseasy.imlog.features.home.topiclog.composer2.ComposerStage
import top.fseasy.imlog.ui.theme.ImlogTheme

@Composable
fun Composer2BottomBar(
  stage: ComposerStage,
  stageRedDots: Set<ComposerStage>,
  sendEnabled: Boolean,
  contextActionState: ComposerInputModeContextActionUiState,
  onSelectStage: (ComposerStage) -> Unit,
  onContextAction: (ComposerBottomContextAction) -> Unit,
  onSend: () -> Unit,
) {

  Row(
      modifier = Modifier.fillMaxWidth().height(36.dp), // 显式锁定底栏高度，避免抖动
      verticalAlignment = Alignment.CenterVertically,
  ) {
    // [1. 左区]：一体化胶囊互斥模式工具条 (Segmented Mode Pill)
    InputModeToolBar(
        stage,
        redDotStages = stageRedDots,
        onSelectStage = onSelectStage,
    )

    // [2. 中区]：动态上下文插槽
    Box(
        modifier = Modifier.weight(1f).padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
      Crossfade(
          targetState = stage,
          animationSpec = tween(durationMillis = 180),
          label = "ContextualSlot",
      ) { currentStage ->
        ComposerInputModeContextActionBar(
            currentStage,
            contextActionState,
            onAction = onContextAction,
        )
      }
    }

    // [3. 右区]：发送按钮
    SendIcon(
        enabled = sendEnabled,
        onClick = onSend,
    )
  }
}

@Composable
private fun SendIcon(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val sendButtonColor by
      animateColorAsState(
          targetValue =
              if (enabled) {
                MaterialTheme.colorScheme.primary
              } else {
                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f)
              },
          label = "SendButtonBgColor",
      )
  val sendIconTint by
      animateColorAsState(
          targetValue =
              if (enabled) {
                MaterialTheme.colorScheme.onPrimary
              } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
              },
          label = "SendButtonIconTint",
      )

  Box(
      modifier =
          modifier
              .size(48.dp) // 1. 外层 Box 设置 48dp，保证无障碍点击热区
              .clickable(
                  enabled = enabled,
                  role = Role.Button,
                  onClick = onClick,
              ),
      contentAlignment = Alignment.Center,
  ) {
    // 2. 内层 Box 才是视觉上的按钮（32dp 圆形背景）
    Box(
        modifier = Modifier.size(36.dp).clip(CircleShape).background(sendButtonColor),
        contentAlignment = Alignment.Center,
    ) {
      Icon(
          painter = painterResource(R.drawable.icon_arrow_upward_alt_48px),
          contentDescription = "Send",
          modifier = Modifier.size(28.dp),
          tint = sendIconTint,
      )
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
private fun Composer2BottomBarPreview() {
  ImlogTheme {
    Surface {
      Column {
        Composer2BottomBar(
            stage = ComposerStage.Default,
            stageRedDots =  emptySet(),
            sendEnabled=  true,
            contextActionState = ComposerInputModeContextActionUiState(
                compressAlbumEnabled = true
            ),
            onSelectStage = {},
            onContextAction = {},
            onSend = {}
        )
      }
    }
  }
}
