package top.fseasy.imlog.ui.components.overlaylayout

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    val registry: OverlayLayoutMetadataRegistry,
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
      registry.queryVisibleThumbnail(initialItemKey, initialWindowBounds)

  /**
   * If you don't report the thumbnail coordinates with [Modifier.recordThumbnailBounds], it'll be
   * in non-geometry mode, which means only background-alpha animation, without size transition.
   *
   * Mainly used for text element.
   */
  val transitionMode =
      if (initialTarget == null) OverlayTransitionMode.Fade else OverlayTransitionMode.Geometry
  val isGeometryMode: Boolean
    get() = transitionMode == OverlayTransitionMode.Geometry

  /** Content layer full screen window */
  var windowBounds by mutableStateOf(initialWindowBounds)
    private set

  /** Content layer target rect */
  var fitRect by mutableStateOf(calculateFitRect(initialWindowBounds, initialTarget?.aspectRatio))
    private set

  fun updateWindowBounds(newBounds: Rect) {
    if (windowBounds != newBounds) {
      windowBounds = newBounds
      fitRect = calculateFitRect(newBounds, initialTarget?.aspectRatio)
    }
  }

  // Animation for the content layer
  // 1. target -> full-screen rect animation.
  val animatedRect = Animatable(initialTarget?.bounds ?: fitRect, RectVectorConverter)
  // 2. target clip rect animation. Used to mask the target part that should be cover by the visual
  // upper part.
  // For example: if an image that is partially out of the timeline screen, then transition of
  //   the out-of-viewport part should be covered by the timeline top-bar.
  //   But because the transition content layer is on the top of the timeline top-bar, so it can't
  //   keep the mask attributes without the clip.
  //   And the clip rect is also very simple: just equal to the original shown part of the target.
  // NOTE: if there is no corner radius of the content, we can just keep the clip rect, the
  // [animatedRect] only works for the condition that draws the clipped corners properly.
  val animatedClipRect = Animatable(initialTarget?.clipBounds ?: fitRect, RectVectorConverter)
  val bgAlpha = Animatable(0f)
  val cornerRadius = Animatable(initialTarget?.cornerRadius ?: 0f)

  /** 触发入场动效 */
  fun runEnterAnimation() {
    coroutineScope.launch {
      coroutineScope {
        launch { bgAlpha.animateTo(1f, tween(TRANSITION_DURATION, easing = FastOutSlowInEasing)) }
        if (isGeometryMode) {
          launch {
            cornerRadius.animateTo(0f, tween(TRANSITION_DURATION, easing = LinearOutSlowInEasing))
          }
          launch {
            animatedRect.animateTo(
                fitRect,
                tween(TRANSITION_DURATION, easing = FastOutSlowInEasing),
            )
          }
          launch {
            animatedClipRect.animateTo(
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

    // 1. 同步取 Alpha（必须在 handover 重置前获取）
    val currentVisualAlpha = contentAlphaProvider?.invoke() ?: bgAlpha.value

    when (transitionMode) {
      OverlayTransitionMode.Fade ->
          coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            bgAlpha.snapTo(currentVisualAlpha)
            bgAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(TRANSITION_DURATION, easing = FastOutSlowInEasing),
            )
            onDismissFinished()
          }
      OverlayTransitionMode.Geometry -> {
        val targetKey = key ?: currentItemKeyProvider?.invoke() ?: initialItemKey

        // 🌟 必须在主线程同步抓取并重置源视图，绝对不能延迟
        val startVRect =
            dismissTransformHandoverProvider?.invoke()?.captureVisualRectAndReset(fitRect)
                ?: fitRect

        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
          // 第一时间接管 overlay 的视觉状态，杜绝任何跳帧闪烁
          bgAlpha.snapTo(currentVisualAlpha)
          animatedRect.snapTo(startVRect)
          animatedClipRect.snapTo(windowBounds)

          // 接管视觉后再查询目标缩略图位置
          val targetThumbnail = registry.queryVisibleThumbnail(targetKey, windowBounds)
          val finalRadius = targetThumbnail?.cornerRadius ?: 0f
          val screenCenter = Offset(windowBounds.width / 2f, windowBounds.height / 2f)
          val finalRect =
              targetThumbnail?.bounds
                  ?: Rect(screenCenter.x, screenCenter.y, screenCenter.x, screenCenter.y)
          val finalClip = targetThumbnail?.clipBounds ?: finalRect

          coroutineScope {
            launch {
              bgAlpha.animateTo(0f, tween(TRANSITION_DURATION, easing = FastOutSlowInEasing))
            }
            launch {
              cornerRadius.animateTo(
                  finalRadius,
                  tween(TRANSITION_DURATION, easing = LinearOutSlowInEasing),
              )
            }
            launch {
              animatedRect.animateTo(
                  finalRect,
                  tween(TRANSITION_DURATION, easing = FastOutSlowInEasing),
              )
            }
            launch {
              animatedClipRect.animateTo(
                  finalClip,
                  tween(TRANSITION_DURATION, easing = FastOutSlowInEasing),
              )
            }
          }
          onDismissFinished()
        }
      }
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
