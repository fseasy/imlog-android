package top.fseasy.imlog.ui.components.overlaylayout

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import top.fseasy.imlog.ui.components.gesture.TransformHandoverChain

private val RectVectorConverter =
    TwoWayConverter<Rect, AnimationVector4D>(
        convertToVector = { AnimationVector4D(it.left, it.top, it.right, it.bottom) },
        convertFromVector = { Rect(it.v1, it.v2, it.v3, it.v4) },
    )

private const val TRANSITION_DURATION = 260

class OverlaySceneState(
    val initialItemKey: Any,
    initialWindowBounds: Rect,
    val registry: OverlayLayoutThumbnailRegistry,
    private val coroutineScope: CoroutineScope,
    private val onDismissFinished: () -> Unit,
) {
  var transitionPhase by mutableStateOf(OverlayTransitionPhase.Entering)
    private set

  // 子组件动态供给的 Providers
  var contentAlphaProvider by mutableStateOf<(() -> Float)?>(null)
  var dismissTransformHandoverProvider by mutableStateOf<(() -> TransformHandoverChain)?>(null)
  var currentItemKeyProvider by mutableStateOf<(() -> Any)?>(null)

  // 背景手势绑定
  var onBgTap by mutableStateOf<(() -> Unit)?>(null)
  var onBgDoubleTap by mutableStateOf<((Offset) -> Unit)?>(null)

  // Geometry define and setting
  private val initialTarget: ThumbnailTarget? =
      if (initialWindowBounds.width > 0f && initialWindowBounds.height > 0f) {
        registry.queryVisibleThumbnail(initialItemKey, initialWindowBounds)
      } else null
  val isGeometryMode = initialTarget != null

  var windowBounds by mutableStateOf(initialWindowBounds)
    private set

  var fitRect by mutableStateOf(calculateFitRect(initialWindowBounds, initialTarget?.aspectRatio))
    private set

  fun updateWindowBounds(newBounds: Rect) {
    if (windowBounds != newBounds) {
      windowBounds = newBounds
      fitRect = calculateFitRect(newBounds, initialTarget?.aspectRatio)
    }
  }

  // 动画驱动器
  val animatedRect = Animatable(initialTarget?.bounds ?: fitRect, RectVectorConverter)
  val bgAlpha = Animatable(0f)
  val cornerRadius = Animatable(initialTarget?.cornerRadius ?: 0f)

  /** 触发入场动效 */
  fun runEnterAnimation() {
    coroutineScope.launch {
      coroutineScope {
        launch { bgAlpha.animateTo(1f, tween(TRANSITION_DURATION, easing = FastOutSlowInEasing)) }
        if (isGeometryMode) {
          launch {
            cornerRadius.animateTo(0f, tween(TRANSITION_DURATION, easing = FastOutSlowInEasing))
          }
          launch {
            animatedRect.animateTo(
                fitRect,
                tween(TRANSITION_DURATION, easing = FastOutSlowInEasing),
            )
          }
        }
      }
      if (transitionPhase == OverlayTransitionPhase.Entering) {
        transitionPhase = OverlayTransitionPhase.Settled
      }
    }
  }

  /** 触发退场动效 */
  fun triggerDismiss(key: Any? = null) {
    if (transitionPhase == OverlayTransitionPhase.Dismissing) return
    transitionPhase = OverlayTransitionPhase.Dismissing

    val targetKey = key ?: currentItemKeyProvider?.invoke() ?: initialItemKey

    // MUST get the alpha first. As following `dismissTransformHandover` will reset the source
    // alpha!
    val currentVisualAlpha = contentAlphaProvider?.invoke() ?: bgAlpha.value
    // 🌟 计算起始 Rect：取绑定的 dismissTransformHandover -> 兜底当前状态
    val startVRect =
        dismissTransformHandoverProvider?.invoke()?.captureVisualRectAndReset(fitRect)
            ?: if (isGeometryMode) fitRect else animatedRect.value

    // NOTE: DO IT in current main thread immediately! To avoid the transform handover frame-flash
    coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
      bgAlpha.snapTo(currentVisualAlpha)
      animatedRect.snapTo(startVRect)

      // 查询退出目的地的缩略图状态
      val targetThumbnail = registry.queryVisibleThumbnail(targetKey, windowBounds)
      val screenCenter = Offset(windowBounds.width / 2f, windowBounds.height / 2f)
      val finalRect =
          targetThumbnail?.bounds
              ?: Rect(screenCenter.x, screenCenter.y, screenCenter.x, screenCenter.y)
      val finalRadius = targetThumbnail?.cornerRadius ?: 0f

      coroutineScope {
        launch { bgAlpha.animateTo(0f, tween(TRANSITION_DURATION, easing = FastOutSlowInEasing)) }
        if (isGeometryMode || targetThumbnail != null) {
          launch {
            cornerRadius.animateTo(
                finalRadius,
                tween(TRANSITION_DURATION, easing = FastOutSlowInEasing),
            )
          }
          launch {
            animatedRect.animateTo(
                finalRect,
                tween(TRANSITION_DURATION, easing = FastOutSlowInEasing),
            )
          }
        }
      }
      onDismissFinished()
    }
  }

  companion object {
    private fun calculateFitRect(bounds: Rect, aspectRatio: Float?): Rect {
      val screenW = bounds.width
      val screenH = bounds.height
      if (screenW <= 0f || screenH <= 0f) return Rect.Zero

      val ratio = aspectRatio ?: (screenW / screenH)
      val containerRatio = screenW / screenH
      val w = if (ratio > containerRatio) screenW else screenH * ratio
      val h = if (ratio > containerRatio) screenW / ratio else screenH
      val center = Offset(screenW / 2f, screenH / 2f)
      return Rect(center.x - w / 2f, center.y - h / 2f, center.x + w / 2f, center.y + h / 2f)
    }
  }
}
