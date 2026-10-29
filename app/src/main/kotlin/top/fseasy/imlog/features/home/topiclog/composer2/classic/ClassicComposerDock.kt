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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import top.fseasy.imlog.R
import top.fseasy.imlog.domain.model.ScreenOrientation
import top.fseasy.imlog.features.home.topiclog.composer2.tabbedcard.SolidActionCard
import top.fseasy.imlog.ui.util.toScreenConfiguration
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ClassicComposerDock(
    state: ComposerDockState,
    textFieldState: TextFieldState,
    onTextFieldFocusChanged: (focused: Boolean) -> Unit,
    onUpdateCachedImeHeightInDp: (ScreenOrientation, heightValueInDp: Float) -> Unit,
    onComposerBarAction: (ComposerBarAction) -> Unit,
    onHoldToRecordAction: (ComposerHoldToRecordAction) -> Unit,
    modifier: Modifier = Modifier,
) {
  val density = LocalDensity.current
  val keyboardController = LocalSoftwareKeyboardController.current
  val orientation = LocalConfiguration.current.toScreenConfiguration()
  val focusManager = LocalFocusManager.current
  var isTextFieldFocused by remember { mutableStateOf(false) }
  val imeJitterGuard = rememberImeJitterGuard()
  val currentCachedHeightPx =
      with(density) { state.cachedImeHeightInDp.get(orientation).dp.roundToPx() }
  val imeAnimationTarget = WindowInsets.imeAnimationTarget
  val imeInsets = WindowInsets.ime
  val navigationBars = WindowInsets.navigationBars

  LaunchedEffect(orientation, density) {
    snapshotFlow {
      val target = imeAnimationTarget.getBottom(density)
      if (target > 0) target else null
    }
        .filterNotNull()
        .distinctUntilChanged()
        .collect { heightPx ->
          // Avoid race-condition dirty data
          if (!imeJitterGuard.canUpdateCache()) return@collect
          val heightDp = with(density) { heightPx.toDp() }
          onUpdateCachedImeHeightInDp(orientation, heightDp.value)
          Timber.i("Update cached ime height = $heightDp")
        }
  }

  // 初始高度设为 0
  val panelHeightAnimation = remember { Animatable(0f) }

  LaunchedEffect(state.activePanel) {
    when (state.activePanel) {
      ActivePanel.ATTACHMENT -> {
        val shouldSkipAnimate = imeInsets.getBottom(density) > 0f
        if (shouldSkipAnimate) {
          // IME 已打开时，再打开键盘切面板 -> 面板瞬间就位，不做动画！让 IME 自己滑下去，从而露出已就位的面板
          panelHeightAnimation.snapTo(currentCachedHeightPx.toFloat())
        } else {
          // IME 不存在，打开键盘切面板 -> 常规情况，平滑向上升起
          panelHeightAnimation.animateTo(
              targetValue = currentCachedHeightPx.toFloat(),
              animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
          )
        }
        // IME should always hide!
        keyboardController?.hide()
        // [isTextFieldFocused] state will be update inside the callback, so don't update it here
        focusManager.clearFocus()
      }
      ActivePanel.NONE -> {
        // Condition: Already closed, do nothing [recomposition due to other state]
        if (panelHeightAnimation.value <= 1e-6f) return@LaunchedEffect
        // condition 1: IME replace the Active panel
        // condition 2: None replace the active panel (directly close)
        // When IME replace the panel, IME will do animation after the system has synced the status,
        // there is a latency
        // around 200ms. So the text field focused status is the best timely way
        val shouldKeepHeight = isTextFieldFocused
        if (shouldKeepHeight) {
          // IME 存在，收起面板 -> 先不动，等待键盘升起，再直接消失
          // 黄金窗口 200~250ms：覆盖 144ms 的系统 IPC 延迟
          val targetHeight =
              withTimeoutOrNull(250.milliseconds) {
                snapshotFlow { imeAnimationTarget.getBottom(density) }.first { it > 0 }
              }

          if (targetHeight != null && targetHeight > 0) {
            // 场景 A：正常软键盘弹起 -> 等键盘顶满后 snapTo(0f) 瞬间交接
            snapshotFlow {
              val current = imeInsets.getBottom(density)
              val target = imeAnimationTarget.getBottom(density)
              current to target
            }
                .first { (current, target) ->
                  target == 0 || current >= target
                }
            panelHeightAnimation.snapTo(0f)
          } else {
            // 场景 B：超时（物理键盘/无软键盘） -> 面板平滑收起退场，把空间还给页面
            panelHeightAnimation.animateTo(
                targetValue = 0f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            )
          }
        } else {
          // IME 不存在，收起面板 -> 常规情况，平滑下降归零
          // 注意：这里动画结束前，panelHeightAnim.value 依然 > 0！
          panelHeightAnimation.animateTo(
              targetValue = 0f,
              animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
          )
        }
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
          activePanel = state.activePanel,
          textFieldState = textFieldState,
          onTextFieldFocusChanged = { focused ->
            // Update internal state first
            isTextFieldFocused = focused
            imeJitterGuard.onFocusChanged(focused)
            onTextFieldFocusChanged(focused)
          },
          onHoldToRecordAction = onHoldToRecordAction,
          onComposerBarAction = onComposerBarAction,
      )

      // 底部自适应高度占位容器
      Box(
          modifier =
              Modifier.fillMaxWidth().layout { measurable, constraints ->
                // 实时 Inset 消费（处于 Layout 阶段，每帧变化不触发 Recomposition）
                val navigationBarPx = navigationBars.getBottom(this)
                val currentImePx = imeInsets.getBottom(this)
                val safeCurrentImePx =
                    imeJitterGuard.sanitizeImeHeight(currentImePx, currentCachedHeightPx)
                val currentPanelPx = panelHeightAnimation.value.toInt()
                // 高度仲裁：谁大取谁，键盘下落与面板升降完美融合
                val finalContainerHeight = maxOf(navigationBarPx, safeCurrentImePx, currentPanelPx)

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
        // 防止“关闭时瞬间消失”：
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
