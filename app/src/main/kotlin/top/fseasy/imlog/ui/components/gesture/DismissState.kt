package top.fseasy.imlog.ui.components.gesture

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs

enum class DismissSource {
  None,
  SwipeDown,
  PredictiveBack,
}

@Stable
class DismissState : TransformHandoverLayer {
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

  // Handover apis
  override fun mapRect(input: Rect): Rect {
    return input.applyTransform(contentScale, contentOffset)
  }

  override fun reset() {
    activeSource = DismissSource.None
    swipeOffset = Offset.Zero
    backProgress = 0f
  }
}

/** @param key only used for invalidation state */
@Composable
fun rememberDismissState(key: String): DismissState {
  return remember(key) { DismissState() }
}
