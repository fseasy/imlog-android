package top.fseasy.imlog.ui.components.zoomable

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Zoomable + Swipe-to-dismiss 状态。
 *
 * @param animateDismiss true 时下拉超过阈值会先做滑出动画再回调 [onDismissRequest]
 */
@Stable
class ZoomableState(
    val minScale: Float = 1f,
    val maxScale: Float = 4f,
    private val scope: CoroutineScope,
    private val onDismissRequest: () -> Unit,
    private val animateDismiss: Boolean = true,
) {
  // ---------- 核心动画状态 ----------
  val scale = Animatable(1f)
  val offset = Animatable(Offset.Zero, Offset.VectorConverter)
  val dismissDragY = Animatable(0f) // 仅用于下拉关闭

  var containerSize by mutableStateOf(IntSize.Zero)

  // ---------- 派生 UI 属性 ----------
  val backgroundAlpha: Float
    get() {
      val h = containerSize.height
      if (h == 0) return 1f
      val progress = (abs(dismissDragY.value) / (h * 0.4f)).coerceIn(0f, 1f)
      return 1f - progress
    }

  val dismissScaleFraction: Float
    get() {
      val h = containerSize.height
      if (h == 0) return 1f
      val progress = (abs(dismissDragY.value) / h).coerceIn(0f, 0.4f)
      return 1f - progress
    }

  // ---------- 内部 ----------
  private var gestureJob: Job? = null

  // ==================== 公共手势入口 ====================

  fun onSingleTap() = onDismissRequest()

  fun onDoubleTap(tapOffset: Offset) {
    gestureJob?.cancel()
    gestureJob = scope.launch {
      val targetScale = if (scale.value > 1.2f) 1f else 3f
      val targetOffset =
          if (targetScale == 1f) {
            Offset.Zero
          } else {
            val center = Offset(containerSize.width / 2f, containerSize.height / 2f)
            val raw = (center - tapOffset) * (targetScale - 1f)
            clampOffset(raw, targetScale)
          }

      // 并行动画
      launch { scale.animateTo(targetScale, tween(250)) }
      launch { offset.animateTo(targetOffset, tween(250)) }
    }
  }

  /** Predictive Back 进度 */
  suspend fun updatePredictiveBackProgress(progress: Float) {
    gestureJob?.cancel()
    val target = (1f - progress * 0.3f).coerceIn(0.7f, 1f)
    scale.snapTo(target)
  }

  suspend fun resetBackState() {
    scale.animateTo(1f, spring())
  }

  // ==================== 核心手势流水线 ====================

  suspend fun processGestures(pointerInputScope: PointerInputScope) {
    pointerInputScope.awaitEachGesture {
      awaitFirstDown(requireUnconsumed = false)
      gestureJob?.cancel() // 打断所有进行中的动画

      var isDismissDragging = false
      var panAccum = Offset.Zero

      do {
        val event = awaitPointerEvent(PointerEventPass.Main)
        if (event.changes.fastAny { it.isConsumed }) break

        val pressed = event.changes.count { it.pressed }

        when {
          pressed >= 2 -> {
            // —— 双指 Pinch + Pan ——
            isDismissDragging = false
            val zoom = event.calculateZoom()
            val pan = event.calculatePan()
            val centroid = event.calculateCentroid()

            val newScale = (scale.value * zoom).coerceIn(minScale, maxScale)
            val containerCenter =
                Offset(
                    containerSize.width / 2f,
                    containerSize.height / 2f,
                )
            val targetOffset =
                if (newScale > 1f) {
                  offset.value + pan - (centroid - containerCenter) * (zoom - 1f)
                } else {
                  Offset.Zero
                }

            scope.launch {
              scale.snapTo(newScale)
              offset.snapTo(clampOffset(targetOffset, newScale))
            }
            event.changes.fastForEach { it.consume() }
          }

          pressed == 1 -> {
            val change = event.changes.first { it.pressed }
            val drag = change.positionChange()

            if (scale.value <= 1.05f) {
              // —— 原图模式：下拉关闭 ——
              if (!isDismissDragging && drag.y > 0 && abs(drag.y) > abs(drag.x)) {
                isDismissDragging = true
              }

              if (isDismissDragging) {
                panAccum += drag
                // 橡皮筋阻尼
                val damped = (panAccum.y * 0.8f).coerceAtLeast(0f)
                scope.launch { dismissDragY.snapTo(damped) }
                change.consume()
              }
            } else {
              // —— 放大模式：单指平移 ——
              val newOffset = clampOffset(offset.value + drag, scale.value)
              scope.launch { offset.snapTo(newOffset) }
              change.consume()
            }
          }
        }
      } while (event.changes.fastAny { it.pressed })

      // —— 手指抬起后的结算 ——
      settleAfterGesture(isDismissDragging)
    }
  }

  // ==================== 私有工具 ====================

  private fun settleAfterGesture(wasDismissDragging: Boolean) {
    if (wasDismissDragging) {
      val threshold = containerSize.height * 0.2f
      if (dismissDragY.value > threshold) {
        scope.launch {
          if (animateDismiss) {
            dismissDragY.animateTo(
                containerSize.height.toFloat(),
                tween(200),
            )
          }
          onDismissRequest()
        }
      } else {
        scope.launch {
          dismissDragY.animateTo(
              0f,
              spring(dampingRatio = 0.8f, stiffness = 400f),
          )
        }
      }
    } else if (scale.value < 1f) {
      // 双指缩放过小，回弹到 1x
      scope.launch {
        scale.animateTo(1f, spring())
        offset.animateTo(Offset.Zero, spring())
      }
    }
  }

  private fun clampOffset(target: Offset, currentScale: Float): Offset {
    if (currentScale <= 1f) return Offset.Zero
    val maxX = containerSize.width * (currentScale - 1f) / 2f
    val maxY = containerSize.height * (currentScale - 1f) / 2f
    return Offset(
        x = target.x.coerceIn(-maxX, maxX),
        y = target.y.coerceIn(-maxY, maxY),
    )
  }
}

@Composable
fun rememberZoomableState(
    minScale: Float = 1f,
    maxScale: Float = 4f,
    onDismiss: () -> Unit,
    animateDismiss: Boolean = true,
): ZoomableState {
  val scope = rememberCoroutineScope()
  return remember(minScale, maxScale, onDismiss, animateDismiss) {
    ZoomableState(
        minScale = minScale,
        maxScale = maxScale,
        scope = scope,
        onDismissRequest = onDismiss,
        animateDismiss = animateDismiss,
    )
  }
}
