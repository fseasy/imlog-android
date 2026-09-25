package top.fseasy.imlog.ui.components.gesture

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Stable
class ZoomableState(
    val minScale: Float = 1f,
    val maxScale: Float = 4f,
) {
  var scale by mutableFloatStateOf(1f)
    internal set

  var offset by mutableStateOf(Offset.Zero)
    internal set

  var containerSize by mutableStateOf(IntSize.Zero)
    internal set

  val isZoomed: Boolean by derivedStateOf { scale > 1.05f }

  private val scaleAnim = Animatable(1f)
  private val offsetAnim = Animatable(Offset.Zero, Offset.VectorConverter)

  /** 双指实时捏合/平移（同步更新，无 Coroutine 调度开销） */
  fun updatePanZoom(zoomChange: Float, panChange: Offset, centroid: Offset) {
    val newScale = (scale * zoomChange).coerceIn(minScale * 0.8f, maxScale * 1.5f)
    val containerCenter = Offset(containerSize.width / 2f, containerSize.height / 2f)
    val newOffset =
        if (newScale > 1f) {
          offset + panChange - (centroid - containerCenter) * (zoomChange - 1f)
        } else {
          Offset.Zero
        }
    scale = newScale
    offset = clampOffset(newOffset, newScale)
  }

  /** 放大模式下单指平移 */
  fun updatePan(drag: Offset) {
    offset = clampOffset(offset + drag, scale)
  }

  /** 双击动画放大/还原 */
  suspend fun toggleZoom(tapOffset: Offset) {
    val targetScale = if (scale > 1.2f) 1f else 2.5f
    val targetOffset =
        if (targetScale == 1f) {
          Offset.Zero
        } else {
          val center = Offset(containerSize.width / 2f, containerSize.height / 2f)
          clampOffset((center - tapOffset) * (targetScale - 1f), targetScale)
        }
    animateTo(targetScale, targetOffset)
  }

  /** 动画平滑过渡 */
  suspend fun animateTo(targetScale: Float, targetOffset: Offset) {
    scaleAnim.snapTo(scale)
    offsetAnim.snapTo(offset)
    coroutineScope {
      launch {
        scaleAnim.animateTo(targetScale, tween(250)) { scale = value }
      }
      launch {
        offsetAnim.animateTo(clampOffset(targetOffset, targetScale), tween(250)) { offset = value }
      }
    }
  }

  /** 手指松开后的边界回弹 */
  suspend fun settle() {
    if (scale < minScale) {
      animateTo(minScale, Offset.Zero)
    } else if (scale > maxScale) {
      animateTo(maxScale, offset)
    }
  }

  /** Predictive Back 系统返回进度支持 */
  suspend fun updatePredictiveBackProgress(progress: Float) {
    scale = (1f - progress * 0.25f).coerceIn(0.75f, 1f)
  }

  suspend fun resetBackState() {
    animateTo(1f, Offset.Zero)
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
): ZoomableState {
  return remember(minScale, maxScale) { ZoomableState(minScale, maxScale) }
}
