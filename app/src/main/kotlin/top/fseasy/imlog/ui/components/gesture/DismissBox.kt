package top.fseasy.imlog.ui.components.gesture

import androidx.activity.compose.PredictiveBackHandler
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
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs

@Composable
fun DismissibleBox(
    modifier: Modifier = Modifier,
    state: DismissState,
    enabled: () -> Boolean = { true },
    drawBackground: Boolean = true, // 独立使用时为 true，配合 OverlayLayout 时为 false
    backgroundColor: Color = Color.Black,
    onDismissRequest: () -> Unit,
    animateSwipeDismiss: Boolean,
    content: @Composable () -> Unit,
) {
  val scope = rememberCoroutineScope()
  val currentEnabled by rememberUpdatedState(enabled)

  // 系统预测性返回
  val isBackEnabled = currentEnabled() && state.activeSource != DismissSource.SwipeDown
  PredictiveBackHandler(enabled = isBackEnabled) { progressFlow ->
    state.onPredictiveBackStart()
    try {
      progressFlow.collect { event ->
        state.updatePredictiveBackProgress(event.progress)
      }
      // 手势完整划完并松手：触发确认退出
      onDismissRequest()
    } catch (e: CancellationException) {
      // 用户中途拉回屏幕边缘取消：平滑回弹复原
      state.cancelPredictiveBack()
      throw e
    }
  }

  Box(
      modifier =
          modifier
              .onSizeChanged { state.containerSize = it }
              .graphicsLayer {
                translationX = state.contentOffset.x
                translationY = state.contentOffset.y
                scaleX = state.contentScale
                scaleY = state.contentScale
              }
              .then(
                  if (drawBackground) {
                    Modifier.background(backgroundColor.copy(alpha = state.backgroundAlpha))
                  } else {
                    Modifier
                  }
              )
              .pointerInput(Unit) {
                awaitEachGesture {
                  awaitFirstDown(requireUnconsumed = false)
                  if (!currentEnabled() || state.activeSource == DismissSource.PredictiveBack) {
                    return@awaitEachGesture
                  }

                  var isDismissDragging = false

                  do {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    if (event.changes.count { it.pressed } > 1) break

                    val change = event.changes.firstOrNull { it.pressed } ?: break
                    if (change.isConsumed) break

                    val drag = change.positionChange()

                    if (!isDismissDragging) {
                      if (drag.y > 0 && abs(drag.y) > abs(drag.x) * 1.3f) {
                        isDismissDragging = true
                        state.onSwipeStart()
                      }
                    }

                    if (isDismissDragging) {
                      state.updateSwipeDrag(drag)
                      change.consume()
                    }
                  } while (event.changes.fastAny { it.pressed })

                  if (isDismissDragging) {
                    scope.launch {
                      state.settleSwipe(
                          animateDismiss = animateSwipeDismiss,
                          onDismissRequest = onDismissRequest,
                      )
                    }
                  }
                }
              }
  ) {
    content()
  }
}
