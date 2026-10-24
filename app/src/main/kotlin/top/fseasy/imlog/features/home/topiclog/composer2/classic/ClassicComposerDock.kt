package top.fseasy.imlog.features.home.topiclog.composer2.classic

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import top.fseasy.imlog.R
import top.fseasy.imlog.domain.model.ScreenOrientation
import top.fseasy.imlog.features.home.topiclog.composer2.tabbedcard.SolidActionCard
import top.fseasy.imlog.ui.util.toScreenConfiguration

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ClassicComposerDock(
    state: ComposerDockState,
    textFieldState: TextFieldState,
    onUpdateCachedImeHeightInDp: (ScreenOrientation, heightValueInDp: Float) -> Unit,
    onToggleAttachment: () -> Unit,
    onSendText: () -> Unit,
    onTextFieldFocusChanged: (focused: Boolean) -> Unit,
    onHoldToRecordAction: (ComposerHoldToRecordAction) -> Unit,
    modifier: Modifier = Modifier,
) {
  val density = LocalDensity.current
  val keyboardController = LocalSoftwareKeyboardController.current
  val orientation = LocalConfiguration.current.toScreenConfiguration()

  val currentCachedHeightPx =
      with(density) { state.cachedImeHeightInDp.get(orientation).dp.roundToPx() }
  val imeAnimationTargetHeightPx = WindowInsets.imeAnimationTarget.getBottom(density)

  LaunchedEffect(imeAnimationTargetHeightPx, orientation, density) {
    if (imeAnimationTargetHeightPx > 0) {
      val imeAnimationTargetHeightDp = with(density) { imeAnimationTargetHeightPx.toDp() }
      onUpdateCachedImeHeightInDp(orientation, imeAnimationTargetHeightDp.value)
    }
  }

  // 初始高度设为 0
  val panelHeightAnimation = remember { Animatable(0f) }
  val isImeOpen = imeAnimationTargetHeightPx > 0
  val imeInsets = WindowInsets.ime
  val navigationBars = WindowInsets.navigationBars

  LaunchedEffect(state.activePanel) {
    when (state.activePanel) {
      ActivePanel.ATTACHMENT -> {
        if (isImeOpen) {
          // IME 已打开时，再打开键盘切面板 -> 面板瞬间就位，不做动画！让 IME 自己滑下去，从而露出已就位的面板
          panelHeightAnimation.snapTo(currentCachedHeightPx.toFloat())
        } else {
          // IME 不存在，打开键盘切面板 -> 常规情况，平滑向上升起
          panelHeightAnimation.animateTo(
              targetValue = currentCachedHeightPx.toFloat(),
              animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
          )
        }
      }
      ActivePanel.NONE -> {
        // 收起面板 -> 平滑下降归零
        // 注意：这里动画结束前，panelHeightAnim.value 依然 > 0！
        panelHeightAnimation.animateTo(
            targetValue = 0f,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        )
      }
    }
  }
  Surface(
      modifier = modifier.fillMaxWidth(),
      color = MaterialTheme.colorScheme.surface, // 与页面主底色保持一致，形成天然遮罩
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {

      // 顶部输入条
      DockFloatingContent(
          mode = state.mode,
          onAttachmentClick = onToggleAttachment,
          onTextFieldFocusChanged = onTextFieldFocusChanged,
          textFieldState = textFieldState,
          onSendText = onSendText,
          onHoldToRecordAction = onHoldToRecordAction,
      )

      // 底部自适应高度占位容器
      Box(
          modifier =
              Modifier.fillMaxWidth().layout { measurable, constraints ->
                // 实时 Inset 消费（处于 Layout 阶段，每帧变化不触发 Recomposition）
                val navigationBarPx = navigationBars.getBottom(this)
                val currentImePx = imeInsets.getBottom(this)
                val currentPanelPx = panelHeightAnimation.value.toInt()
                // 高度仲裁：谁大取谁，键盘下落与面板升降完美融合
                val finalContainerHeight = maxOf(navigationBarPx, currentImePx, currentPanelPx)

                val placeable =
                    measurable.measure(
                        constraints.copy(
                            minHeight = finalContainerHeight,
                            maxHeight = finalContainerHeight,
                        )
                    )
                layout(placeable.width, finalContainerHeight) {
                  placeable.place(0, 0)
                }
              }
      ) {
        // 解决“关闭时瞬间消失”的 Bug：
        // 判断条件不再是单一的 isAttachmentOpen，而是动画还在进行（value > 0）时就必须保留！
        val isPanelVisible =
            state.activePanel == ActivePanel.ATTACHMENT || panelHeightAnimation.value > 0f

        if (isPanelVisible) {
          AttachmentPanel(
              modifier =
                  Modifier.fillMaxWidth()
                      // 始终固定为其自身标准高度，靠外部 Box 的 clip/layout 裁剪，
                      // 防止面板内部组件在高度变化时被不断重新压缩重测
                      .height(with(density) { currentCachedHeightPx.toDp() })
                      // 因为外部的高度本身已经包含了 navigation bar height, 而面板内容直通屏幕物理底端，
                      // 锁内部内部必须垫出导航栏安全区.
                      .navigationBarsPadding()
          )
        }
      }
    }
  }
}

@Composable
fun AttachmentPanel(modifier: Modifier) {
  Row(
      modifier = modifier,
      horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    SolidActionCard(
        icon = ImageVector.vectorResource(R.drawable.icon_mic),
        label = "Recorder",
        onClick = {},
    )
    SolidActionCard(
        icon = ImageVector.vectorResource(R.drawable.icon_camera_48px),
        label = "Camera",
        onClick = {},
    )
  }
}
