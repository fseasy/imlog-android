package top.fseasy.imlog.ui.components.overlaylayout

import androidx.compose.ui.geometry.Rect

interface OverlayLayoutScope {
  fun dismiss(
      itemKey: Any,
      transform: ((baseRect: Rect) -> Rect)? = null,
  )

  /** 允许内部组件在非转场稳定态（Entered）动态绑定背景透明度（如手势下拉联动）。 在入场与退场动画执行期间，该供给者被忽略，由 OverlayLayout 全权托管。 */
  fun bindBackgroundAlpha(alphaProvider: () -> Float)
}
