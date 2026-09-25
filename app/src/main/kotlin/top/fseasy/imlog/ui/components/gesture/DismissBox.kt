package top.fseasy.imlog.ui.components.gesture

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastAny
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs

enum class DismissSource {
  None,
  SwipeDown,
  PredictiveBack,
}

@Stable
class DismissState {
  var containerSize by mutableStateOf(IntSize.Zero)
    internal set

  var activeSource by mutableStateOf(DismissSource.None)
    private set

  var swipeOffset by mutableStateOf(Offset.Zero)
    private set

  var backProgress by mutableFloatStateOf(0f)
    private set

  private val swipeAnimatable = Animatable(Offset.Zero, Offset.VectorConverter)
  private val backProgressAnimatable = Animatable(0f)

  val contentOffset: Offset
    get() =
        when (activeSource) {
          DismissSource.SwipeDown -> swipeOffset
          else -> Offset.Zero
        }

  val contentScale: Float
    get() =
        when (activeSource) {
          DismissSource.SwipeDown -> {
            val h = containerSize.height.takeIf { it > 0 } ?: return 1f
            (1f - (abs(swipeOffset.y) / h * 0.4f)).coerceIn(0.65f, 1f)
          }

          DismissSource.PredictiveBack -> {
            (1f - backProgress * 0.25f).coerceIn(0.75f, 1f)
          }

          DismissSource.None -> 1f
        }

  val backgroundAlpha: Float
    get() =
        when (activeSource) {
          DismissSource.SwipeDown -> {
            val h = containerSize.height.takeIf { it > 0 } ?: return 1f
            (1f - (abs(swipeOffset.y) / (h * 0.45f))).coerceIn(0f, 1f)
          }

          DismissSource.PredictiveBack -> {
            (1f - backProgress * 1.2f).coerceIn(0f, 1f)
          }

          DismissSource.None -> 1f
        }

  fun onSwipeStart() {
    if (activeSource == DismissSource.None) {
      activeSource = DismissSource.SwipeDown
    }
  }

  fun updateSwipeDrag(drag: Offset) {
    if (activeSource != DismissSource.SwipeDown) return
    val newY = (swipeOffset.y + drag.y * 0.75f).coerceAtLeast(0f)
    val newX = swipeOffset.x + drag.x * 0.75f
    swipeOffset = Offset(newX, newY)
  }

  suspend fun settleSwipe(animateDismiss: Boolean, onDismissRequest: () -> Unit) {
    val threshold = containerSize.height * 0.15f
    if (swipeOffset.y > threshold) {
      if (animateDismiss) {
        swipeAnimatable.snapTo(swipeOffset)
        swipeAnimatable.animateTo(
            targetValue = Offset(swipeOffset.x, containerSize.height.toFloat())
        ) {
          swipeOffset = value
        }
      }
      onDismissRequest()
    } else {
      swipeAnimatable.snapTo(swipeOffset)
      swipeAnimatable.animateTo(
          targetValue = Offset.Zero,
          animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
      ) {
        swipeOffset = value
      }
      activeSource = DismissSource.None
    }
  }

  fun onPredictiveBackStart() {
    activeSource = DismissSource.PredictiveBack
  }

  fun updatePredictiveBackProgress(progress: Float) {
    backProgress = progress
  }

  suspend fun cancelPredictiveBack() {
    backProgressAnimatable.snapTo(backProgress)
    backProgressAnimatable.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 400f)) {
      backProgress = value
    }
    activeSource = DismissSource.None
  }
}

@Composable
fun rememberDismissState(): DismissState {
  return remember { DismissState() }
}

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
                  val down = awaitFirstDown(requireUnconsumed = false)
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

/** 几何投影纯函数：计算最终物理视觉矩形 */
fun ZoomableState.computeVisualRect(
    baseRect: Rect,
    dismissState: DismissState? = null,
): Rect {
  val dismissScale = dismissState?.contentScale ?: 1f
  val dismissOffset = dismissState?.contentOffset ?: Offset.Zero

  val totalScale = scale * dismissScale
  val totalOffset = offset + dismissOffset

  val visualCenter = baseRect.center + totalOffset
  val visualWidth = baseRect.width * totalScale
  val visualHeight = baseRect.height * totalScale

  return Rect(
      left = visualCenter.x - visualWidth / 2f,
      top = visualCenter.y - visualHeight / 2f,
      right = visualCenter.x + visualWidth / 2f,
      bottom = visualCenter.y + visualHeight / 2f,
  )
}

fun computeVisualRect(
    baseRect: Rect,
    zoomableState: ZoomableState? = null,
    dismissState: DismissState? = null,
): Rect {
  val zoomScale = zoomableState?.scale ?: 1f
  val zoomOffset = zoomableState?.offset ?: Offset.Zero
  val dismissScale = dismissState?.contentScale ?: 1f
  val dismissOffset = dismissState?.contentOffset ?: Offset.Zero

  val totalScale = zoomScale * dismissScale
  val totalOffset = zoomOffset + dismissOffset

  val visualCenter = baseRect.center + totalOffset
  val visualWidth = baseRect.width * totalScale
  val visualHeight = baseRect.height * totalScale

  return Rect(
      left = visualCenter.x - visualWidth / 2f,
      top = visualCenter.y - visualHeight / 2f,
      right = visualCenter.x + visualWidth / 2f,
      bottom = visualCenter.y + visualHeight / 2f,
  )
}
