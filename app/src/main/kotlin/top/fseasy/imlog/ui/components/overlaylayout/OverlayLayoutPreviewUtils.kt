package top.fseasy.imlog.ui.components.overlaylayout

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import top.fseasy.imlog.ui.components.gesture.TransformHandoverChain

/** 专为 @Preview 和本地单元测试提供的 OverlayLayoutScope Mock 实现 */
object PreviewOverlayLayoutScope : OverlayLayoutScope {

  override val transitionPhase: OverlayTransitionPhase
    get() = OverlayTransitionPhase.Settled

  override fun dismiss(itemKey: Any?) {
    // Preview 中无需实际行为，空实现即可
  }

  override fun Modifier.overlayInteractiveTarget(
      itemKey: Any?,
      backgroundAlphaProvider: (() -> Float)?,
      dismissTransformHandoverProvider: (() -> TransformHandoverChain)?,
  ): Modifier {
    // Preview 阶段不跑手势联动，直接返回原 Modifier，保持静态渲染
    return this
  }

  override fun configureBackgroundTap(
      onTap: (() -> Unit)?,
      onDoubleTap: ((Offset) -> Unit)?,
  ) {
    // 空实现
  }
}
