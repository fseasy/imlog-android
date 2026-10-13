package top.fseasy.imlog.ui.components.contextmenu

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * Click Modifier that mainly for popup context menu when long click. If you want to show context
 * menu when long click, put the menu showing function to the [onLongClickWithPosition].
 *
 * @param onClick function for single click(no context menu). you can bind the target by lambda
 * @param onLongClickWithPosition function for long click, with a param injection of click position
 *   (absolute in the Window).
 *
 *   Mostly, use a lambda that capture the target and then call [ContextMenuState.show]
 */
fun Modifier.contextMenuClickable(
    onClick: (() -> Unit)? = null,
    onLongClickWithPosition: (windowOffset: IntOffset) -> Unit,
): Modifier = this then ContextMenuClickableElement(onClick, onLongClickWithPosition)

private data class ContextMenuClickableElement(
    val onClick: (() -> Unit)?,
    val onLongClickWithPosition: (IntOffset) -> Unit,
) : ModifierNodeElement<ContextMenuClickableNode>() {

  override fun create(): ContextMenuClickableNode =
      ContextMenuClickableNode(onClick, onLongClickWithPosition)

  override fun update(node: ContextMenuClickableNode) {
    node.onClick = onClick
    node.onLongClickWithPosition = onLongClickWithPosition
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "contextMenuClickable"
    properties["onClick"] = onClick
    properties["onLongClickWithPosition"] = onLongClickWithPosition
  }
}

private class ContextMenuClickableNode(
    var onClick: (() -> Unit)?,
    var onLongClickWithPosition: (IntOffset) -> Unit,
) : DelegatingNode(), GlobalPositionAwareModifierNode, PointerInputModifierNode {

  private var itemPositionInWindow: Offset = Offset.Zero

  // 追踪组件在屏幕中的绝对坐标
  override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
    itemPositionInWindow = coordinates.positionInWindow()
  }

  // 处理手势：使用 SuspendingPointerInputModifierNode 代理底层手势
  private val pointerInputNode =
      delegate(
          SuspendingPointerInputModifierNode {
            detectTapGestures(
                onTap = {
                  onClick?.invoke()
                },
                onLongPress = { localOffset ->
                  // detectTapGestures 原生自带 localOffset，且触发时会正确处理手势消费
                  val globalOffset =
                      IntOffset(
                          (itemPositionInWindow.x + localOffset.x).roundToInt(),
                          (itemPositionInWindow.y + localOffset.y).roundToInt(),
                      )
                  onLongClickWithPosition(globalOffset)
                },
            )
          }
      )

  override fun onPointerEvent(
      pointerEvent: PointerEvent,
      pass: PointerEventPass,
      bounds: IntSize,
  ) {
    pointerInputNode.onPointerEvent(pointerEvent, pass, bounds)
  }

  override fun onCancelPointerInput() {
    pointerInputNode.onCancelPointerInput()
  }
}
