package top.fseasy.imlog.features.home.topiclog.composer2

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.platform.InspectorInfo

fun Modifier.tapOrHoldToRecordGesture(
    onTap: () -> Unit,
    onHoldStart: (initialOffsetInRoot: Offset) -> Unit,
    onMove: (currentOffsetInRoot: Offset) -> Unit,
    onRelease: (Offset) -> Unit,
): Modifier = this then TapOrHoldToTalkElement(onTap, onHoldStart, onMove, onRelease)

private data class TapOrHoldToTalkElement(
    val onTap: () -> Unit,
    val onHoldStart: (Offset) -> Unit,
    val onMove: (Offset) -> Unit,
    val onRelease: (Offset) -> Unit,
) : ModifierNodeElement<TapOrHoldToTalkNode>() {

  override fun create(): TapOrHoldToTalkNode =
      TapOrHoldToTalkNode(onTap, onHoldStart, onMove, onRelease)

  override fun update(node: TapOrHoldToTalkNode) {
    node.update(onTap, onHoldStart, onMove, onRelease)
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "composerHoldToTalkGesture"
  }
}

private class TapOrHoldToTalkNode(
    var onTap: () -> Unit,
    var onHoldStart: (Offset) -> Unit,
    var onMove: (Offset) -> Unit,
    var onRelease: (Offset) -> Unit,
) : DelegatingNode(), PointerInputModifierNode {

  fun update(
      onTap: () -> Unit,
      onHoldStart: (Offset) -> Unit,
      onMove: (Offset) -> Unit,
      onRelease: (Offset) -> Unit,
  ) {
    this.onTap = onTap
    this.onHoldStart = onHoldStart
    this.onMove = onMove
    this.onRelease = onRelease
  }

  private val pointerInputNode =
      delegate(
          SuspendingPointerInputModifierNode {
            awaitEachGesture {
              val down = awaitFirstDown(requireUnconsumed = false)
              var lastPosition = down.position
              val releasedBeforeTimeout =
                  withTimeoutOrNull(200L) {
                    while (true) {
                      val event = awaitPointerEvent()
                      // `it.id == down.id`：无论屏幕上后来按下了多少根手指，只盯住最初按下的那根手指；
                      // `?: break`：如果被追踪的手指因意外事件凭空丢失了，立刻退出循环。
                      val change = event.changes.firstOrNull { it.id == down.id } ?: break
                      if (!change.pressed) {
                        onTap()
                        break
                      }
                      lastPosition = change.position
                    }
                  } == null

              if (releasedBeforeTimeout) {
                // 直接使用 Node 内部能力将相对坐标转为 Root 绝对坐标
                val toRoot = { local: Offset ->
                  if (isAttached) requireLayoutCoordinates().localToRoot(local) else local
                }
                val startPosition = toRoot(lastPosition)
                onHoldStart(startPosition)
                onMove(startPosition)

                while (true) {
                  val event = awaitPointerEvent()
                  val change = event.changes.firstOrNull { it.id == down.id } ?: break

                  if (!change.pressed) {
                    onRelease(toRoot(change.position))
                    break
                  }

                  change.consume()
                  onMove(toRoot(change.position))
                }
              }
            }
          }
      )

  override fun onPointerEvent(
      pointerEvent: androidx.compose.ui.input.pointer.PointerEvent,
      pass: androidx.compose.ui.input.pointer.PointerEventPass,
      bounds: androidx.compose.ui.unit.IntSize,
  ) {
    pointerInputNode.onPointerEvent(pointerEvent, pass, bounds)
  }

  override fun onCancelPointerInput() {
    pointerInputNode.onCancelPointerInput()
  }
}
