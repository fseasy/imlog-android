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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Zoomable + Swipe-to-dismiss 状态管理
 *
 * @param animateDismiss true 时下拉超过阈值会先做滑出动画再回调 [onDismissRequest]
 */
@Stable
class ZoomableState(
    val minScale: Float = 1f,
    val maxScale: Float = 4f,
    private val scope: CoroutineScope,
    private val onDismissRequest: ZoomableState.() -> Unit,
    private val onSingleTap: ZoomableState.() -> Unit = onDismissRequest,
    private val animateDismiss: Boolean = true,
) {
  // ---------- 核心动画状态 ----------
  val scale = Animatable(1f)
  val offset = Animatable(Offset.Zero, Offset.VectorConverter)

  // 下拉手势物理位移（X 与 Y 均为 Animatable，保证回弹平滑）
  val dismissDragX = Animatable(0f)
  val dismissDragY = Animatable(0f)

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

  // ==================== 物理矩形计算核心 ====================

  /**
   * 核心补全：根据传入的全屏基准 [baseRect]（即 Fit 居中全屏时的矩形）， 结合当前的放大比例、平移 offset、下拉位移及缩放， 精确计算出图片当前在屏幕/Window
   * 坐标系上的实际视觉 [Rect]。
   */
  fun computeVisualRect(baseRect: Rect): Rect {
    // 综合缩放系数（双指放大 * 下拉微缩）
    val totalScale = scale.value * dismissScaleFraction
    // 综合物理位移（双指移动 + 下拉跟随）
    val totalOffset = offset.value + Offset(dismissDragX.value, dismissDragY.value)

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

  // ==================== 公共手势入口 ====================

  fun singleTap() = this.onSingleTap()

  fun doubleTap(tapOffset: Offset) {
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

  fun dismiss() = this.onDismissRequest()

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
            val containerCenter = Offset(containerSize.width / 2f, containerSize.height / 2f)
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
                // 橡皮筋阻尼：Y 轴主导下拉，X 轴跟随偏移
                val dampedY = (panAccum.y * 0.8f).coerceAtLeast(0f)
                val dampedX = panAccum.x * 0.8f
                scope.launch {
                  dismissDragY.snapTo(dampedY)
                  dismissDragX.snapTo(dampedX)
                }
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
      val threshold = containerSize.height * 0.15f
      if (dismissDragY.value > threshold) {
        scope.launch {
          if (animateDismiss) {
            dismissDragY.animateTo(
                containerSize.height.toFloat(),
                tween(200),
            )
          }
          // 触发回调，this 作为参数自然可用
          onDismissRequest(this@ZoomableState)
        }
      } else {
        // 未达阈值：X 和 Y 同步弹簧复原
        scope.launch {
          launch {
            dismissDragY.animateTo(
                0f,
                spring(dampingRatio = 0.8f, stiffness = 400f),
            )
          }
          launch {
            dismissDragX.animateTo(
                0f,
                spring(dampingRatio = 0.8f, stiffness = 400f),
            )
          }
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

// ==================== Modifier 与 工厂函数 ====================

@Composable
fun rememberZoomableState2(
    minScale: Float = 1f,
    maxScale: Float = 4f,
    onDismissRequest: ZoomableState.() -> Unit,
    onSingleTap: ZoomableState.() -> Unit = onDismissRequest,
    animateDismiss: Boolean = true,
): ZoomableState {
  val scope = rememberCoroutineScope()
  return remember(minScale, maxScale, onDismissRequest, onSingleTap, animateDismiss) {
    ZoomableState(
        minScale = minScale,
        maxScale = maxScale,
        scope = scope,
        onDismissRequest = onDismissRequest,
        onSingleTap = onSingleTap,
        animateDismiss = animateDismiss,
    )
  }
}
