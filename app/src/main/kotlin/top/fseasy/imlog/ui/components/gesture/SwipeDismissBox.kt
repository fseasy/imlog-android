package top.fseasy.imlog.ui.components.gesture

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.util.fastAny
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun SwipeDismissBox(
  modifier: Modifier = Modifier,
  state: SwipeDismissState,
  enabled: () -> Boolean = { true }, // 👈 改为 Lambda Provider
  backgroundColor: Color = Color.Black,
  content: @Composable () -> Unit,
) {
  val scope = rememberCoroutineScope()
  // 用 rememberUpdatedState 保证手势块内总是读到最新的判断
  val currentEnabled by rememberUpdatedState(enabled)

  Box(
      modifier = modifier
          .onSizeChanged { state.containerSize = it }
          // 优化背景透明度：通过 drawBehind 或 modifier 延迟读取，避免重组
          .graphicsLayer {
            translationX = state.dragOffset.x
            translationY = state.dragOffset.y
            scaleX = state.dismissScale
            scaleY = state.dismissScale
          }
          .background(backgroundColor.copy(alpha = state.backgroundAlpha))
          // 注意 key 传 Unit，避免手势处理因重组被中断重建
          .pointerInput(Unit) {
            awaitEachGesture {
              val down = awaitFirstDown(requireUnconsumed = false)

              // 👈 只有在手指按下时才执行 Lambda，完全不会触发 Composable 重组！
              if (!currentEnabled()) return@awaitEachGesture

              var isDismissDragging = false

              do {
                val event = awaitPointerEvent(PointerEventPass.Main)
                if (event.changes.count { it.pressed } > 1) break

                val change = event.changes.firstOrNull { it.pressed } ?: break

                // 👈 另外加一层保障：如果内层 Zoomable 已经消费了单指平移，外层直接跳过
                if (change.isConsumed) break

                val drag = change.positionChange()

                if (!isDismissDragging) {
                  if (drag.y > 0 && abs(drag.y) > abs(drag.x) * 1.3f) {
                    isDismissDragging = true
                  }
                }

                if (isDismissDragging) {
                  state.updateDrag(drag)
                  change.consume()
                }
              } while (event.changes.fastAny { it.pressed })

              if (isDismissDragging) {
                scope.launch { state.settle(animateDismiss = true) }
              }
            }
          }
  ) {
    content()
  }
}
