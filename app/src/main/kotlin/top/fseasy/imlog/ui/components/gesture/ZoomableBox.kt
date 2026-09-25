package top.fseasy.imlog.ui.components.gesture

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import kotlinx.coroutines.launch

@Composable
fun ZoomableBox(
  modifier: Modifier = Modifier,
  state: ZoomableState = rememberZoomableState(),
  onSingleTap: () -> Unit = {},
  content: @Composable () -> Unit,
) {
  val scope = rememberCoroutineScope()
  val currentOnSingleTap by rememberUpdatedState(onSingleTap)

  Box(
      modifier = modifier
          .onSizeChanged { state.containerSize = it }
          // 1. 点击事件处理（双击放大、单击回调）
          .pointerInput(Unit) {
            detectTapGestures(
                onDoubleTap = { tapOffset ->
                  scope.launch { state.toggleZoom(tapOffset) }
                },
                onTap = { currentOnSingleTap() }
            )
          }
          // 2. 缩放与平移手势处理
          .pointerInput(state) {
            awaitEachGesture {
              awaitFirstDown(requireUnconsumed = false)

              do {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val pressedCount = event.changes.count { it.pressed }

                when {
                  // 双指 Pinch
                  pressedCount >= 2 -> {
                    val zoom = event.calculateZoom()
                    val pan = event.calculatePan()
                    val centroid = event.calculateCentroid()
                    state.updatePanZoom(zoom, pan, centroid)
                    event.changes.fastForEach { it.consume() }
                  }
                  // 单指 Pan（仅在放大后消费事件；未放大时不消费，自然留给外层下拉）
                  pressedCount == 1 && state.isZoomed -> {
                    val change = event.changes.first { it.pressed }
                    val drag = change.positionChange()
                    state.updatePan(drag)
                    change.consume()
                  }
                }
              } while (event.changes.fastAny { it.pressed })

              // 抬手结算（缩放过大或过小时回弹）
              scope.launch { state.settle() }
            }
          }
          .graphicsLayer {
            scaleX = state.scale
            scaleY = state.scale
            translationX = state.offset.x
            translationY = state.offset.y
          }
  ) {
    content()
  }
}