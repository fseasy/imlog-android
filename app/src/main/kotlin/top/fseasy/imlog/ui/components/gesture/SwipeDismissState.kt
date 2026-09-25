package top.fseasy.imlog.ui.components.gesture

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs

@Stable
class SwipeDismissState(
    private val onDismissRequest: () -> Unit,
) {
  var dragOffset by mutableStateOf(Offset.Zero)
    internal set

  var containerSize by mutableStateOf(IntSize.Zero)
    internal set

  private val offsetAnim = Animatable(Offset.Zero, Offset.VectorConverter)

  val backgroundAlpha: Float
    get() {
      val h = containerSize.height.takeIf { it > 0 } ?: return 1f
      return (1f - (abs(dragOffset.y) / (h * 0.45f))).coerceIn(0f, 1f)
    }

  val dismissScale: Float
    get() {
      val h = containerSize.height.takeIf { it > 0 } ?: return 1f
      return (1f - (abs(dragOffset.y) / h * 0.4f)).coerceIn(0.65f, 1f)
    }

  fun updateDrag(drag: Offset) {
    // 橡皮筋阻尼：向下拖动带轻微 X 轴跟随
    val newY = (dragOffset.y + drag.y * 0.75f).coerceAtLeast(0f)
    val newX = dragOffset.x + drag.x * 0.75f
    dragOffset = Offset(newX, newY)
  }

  suspend fun settle(animateDismiss: Boolean) {
    val threshold = containerSize.height * 0.15f
    if (dragOffset.y > threshold) {
      if (animateDismiss) {
        offsetAnim.snapTo(dragOffset)
        offsetAnim.animateTo(
            targetValue = Offset(dragOffset.x, containerSize.height.toFloat()),
            animationSpec = tween(180),
        ) {
          dragOffset = value
        }
      }
      onDismissRequest()
    } else {
      // 未达阈值回弹归位
      offsetAnim.snapTo(dragOffset)
      offsetAnim.animateTo(
          targetValue = Offset.Zero,
          animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
      ) {
        dragOffset = value
      }
    }
  }
}

@Composable
fun rememberSwipeDismissState(
    onDismissRequest: () -> Unit,
): SwipeDismissState {
  val currentOnDismiss by rememberUpdatedState(onDismissRequest)
  return remember { SwipeDismissState(onDismissRequest = { currentOnDismiss() }) }
}

