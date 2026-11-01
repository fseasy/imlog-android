package top.fseasy.imlog.features.home.topiclog.composer2

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntSize

/**
 * see also
 * [Modifier.tapOrHoldToRecordGesture][top.fseasy.imlog.features.home.topiclog.composer2.tapOrHoldToRecordGesture]
 */
fun Modifier.holdToRecordGesture(
  onHoldStart: (initialOffsetInRoot: Offset) -> Unit,
  onMove: (currentOffsetInRoot: Offset) -> Unit,
  onRelease: (finalOffsetInRoot: Offset) -> Unit,
): Modifier = this then HoldToRecordElement(onHoldStart, onMove, onRelease)

private data class HoldToRecordElement(
  val onHoldStart: (Offset) -> Unit,
  val onMove: (Offset) -> Unit,
  val onRelease: (Offset) -> Unit,
) : ModifierNodeElement<HoldToRecordNode>() {

  override fun create(): HoldToRecordNode =
    HoldToRecordNode(onHoldStart, onMove, onRelease)

  override fun update(node: HoldToRecordNode) {
    node.update(onHoldStart, onMove, onRelease)
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "composerHoldToRecordGesture"
  }
}

private class HoldToRecordNode(
  var onHoldStart: (Offset) -> Unit,
  var onMove: (Offset) -> Unit,
  var onRelease: (Offset) -> Unit,
) : DelegatingNode(), PointerInputModifierNode {

  fun update(
    onHoldStart: (Offset) -> Unit,
    onMove: (Offset) -> Unit,
    onRelease: (Offset) -> Unit,
  ) {
    this.onHoldStart = onHoldStart
    this.onMove = onMove
    this.onRelease = onRelease
  }

  private val pointerInputNode =
    delegate(
        SuspendingPointerInputModifierNode {
          awaitEachGesture {
            // 1. 等待手指按下
            val down = awaitFirstDown(requireUnconsumed = false)

            val toRoot = { local: Offset ->
              if (isAttached) requireLayoutCoordinates().localToRoot(local) else local
            }

            val startPosition = toRoot(down.position)
            // 2. 按下瞬间立即触发开始
            onHoldStart(startPosition)

            // 3. 追踪手指移动与释放
            while (true) {
              val event = awaitPointerEvent()
              val change = event.changes.firstOrNull { it.id == down.id } ?: break

              if (!change.pressed) {
                // 手指松开
                onRelease(toRoot(change.position))
                break
              }

              // 消耗事件并触发移动回调
              change.consume()
              onMove(toRoot(change.position))
            }
          }
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
