package top.fseasy.imlog.ui.components.contextmenu

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider

// 1. 适合【横向气泡菜单】的定位器（居中对齐手指，优先在上方，触顶翻转到下方）
class HorizontalBubbleMenuPositionProvider(
    private val touchOffset: IntOffset,
    private val margin: Int = 20,
) : PopupPositionProvider {
  override fun calculatePosition(
      anchorBounds: IntRect,
      windowSize: IntSize,
      layoutDirection: LayoutDirection,
      popupContentSize: IntSize,
  ): IntOffset {
    val x =
        (touchOffset.x - popupContentSize.width / 2).coerceIn(
            16,
            windowSize.width - popupContentSize.width - 16,
        )
    val y =
        if (touchOffset.y - popupContentSize.height - margin >= 16) {
          touchOffset.y - popupContentSize.height - margin // 上方
        } else {
          touchOffset.y + margin // 下方
        }
    return IntOffset(x, y)
  }
}

// 2. 适合【会话列表纵向菜单】的定位器（左/右对齐手指，优先在下方，触底翻转到上方）
class VerticalListMenuPositionProvider(
    private val touchOffset: IntOffset,
    private val margin: Int = 10,
) : PopupPositionProvider {
  override fun calculatePosition(
      anchorBounds: IntRect,
      windowSize: IntSize,
      layoutDirection: LayoutDirection,
      popupContentSize: IntSize,
  ): IntOffset {
    val x =
        if (touchOffset.x + popupContentSize.width <= windowSize.width - 16) {
              touchOffset.x
            } else {
              touchOffset.x - popupContentSize.width
            }
            .coerceAtLeast(16)

    val y =
        if (touchOffset.y + popupContentSize.height + margin <= windowSize.height - 16) {
              touchOffset.y + margin // 下方
            } else {
              touchOffset.y - popupContentSize.height - margin // 向上翻转
            }
            .coerceAtLeast(16)

    return IntOffset(x, y)
  }
}
