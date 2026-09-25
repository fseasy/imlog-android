package top.fseasy.imlog.ui.components.sharedtransition

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// 目标位置结构体
data class TransitionSource(
    val key: Any,
    val boundsInWindow: Rect,
    val cornerRadius: Float = 0f,
    val aspectRatio: Float? = null,
)

@Stable
class ImageTransitionController {
  var activeSource by mutableStateOf<TransitionSource?>(null)
    private set

  fun show(key: Any, bounds: Rect, cornerRadius: Float = 0f, aspectRatio: Float? = null) {
    activeSource = TransitionSource(key, bounds, cornerRadius, aspectRatio)
  }

  fun close() {
    activeSource = null
  }
}

val LocalImageTransitionController = staticCompositionLocalOf<ImageTransitionController?> { null }

private val RectVectorConverter =
    TwoWayConverter<Rect, AnimationVector4D>(
        convertToVector = { AnimationVector4D(it.left, it.top, it.right, it.bottom) },
        convertFromVector = { Rect(it.v1, it.v2, it.v3, it.v4) },
    )

private const val OVERLAY_ANIM_DURATION = 260

/** 通用转场浮层容器 */
@Composable
fun ImageTransitionOverlay(
    controller: ImageTransitionController,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
    /** 内容插槽：把计算好的当前动画 Rect 传给里面的 Content 里面可以是简单的图片，也可以是带 ZoomableState 的全功能查看器 */
    overlayContent:
        @Composable
        (source: TransitionSource, onDismiss: () -> Unit, animatedRect: Rect) -> Unit,
) {
  CompositionLocalProvider(LocalImageTransitionController provides controller) {
    Box(modifier = modifier.fillMaxSize()) {
      // 1. 底层常规 UI（聊天列表）
      content()

      // 2. 顶层转场浮层
      controller.activeSource?.let { source ->
        TransitionStage(
            source = source,
            onDismissFinished = { controller.close() },
            overlayContent = overlayContent,
        )
      }
    }
  }
}

@Composable
private fun TransitionStage(
    source: TransitionSource,
    onDismissFinished: () -> Unit,
    overlayContent: @Composable (TransitionSource, onDismiss: () -> Unit, Rect) -> Unit,
) {
  val density = LocalDensity.current
  val coroutineScope = rememberCoroutineScope()

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val containerWidth = constraints.maxWidth.toFloat()
    val containerHeight = constraints.maxHeight.toFloat()

    // 计算居中全屏目标 Rect
    val fitRect =
        remember(containerWidth, containerHeight, source.aspectRatio) {
          val imageRatio = source.aspectRatio ?: (containerWidth / containerHeight)
          val containerRatio = containerWidth / containerHeight
          val w = if (imageRatio > containerRatio) containerWidth else containerHeight * imageRatio
          val h = if (imageRatio > containerRatio) containerWidth / imageRatio else containerHeight
          val l = (containerWidth - w) / 2f
          val t = (containerHeight - h) / 2f
          Rect(l, t, l + w, t + h)
        }

    // 转场物理量驱动器
    val animatedRect = remember { Animatable(source.boundsInWindow, RectVectorConverter) }
    val bgAlpha = remember { Animatable(0f) }
    val cornerRadius = remember { Animatable(source.cornerRadius) }
    var isDismissing by remember { mutableStateOf(false) }

    // 入场动画
    LaunchedEffect(Unit) {
      coroutineScope.launch {
        bgAlpha.animateTo(1f, tween(OVERLAY_ANIM_DURATION, easing = FastOutSlowInEasing))
      }
      coroutineScope.launch {
        cornerRadius.animateTo(0f, tween(OVERLAY_ANIM_DURATION, easing = FastOutSlowInEasing))
      }
      animatedRect.animateTo(fitRect, tween(OVERLAY_ANIM_DURATION, easing = FastOutSlowInEasing))
    }

    // 退场动画：由内层任意时刻触发
    val triggerDismiss: (currentVisualRect: Rect?) -> Unit = { visualRect ->
      if (!isDismissing) {
        isDismissing = true
        coroutineScope.launch {
          // 如果手势下拉过，就以手势松手瞬间的 visualRect 为起点飞回；否则以当前 animatedRect 飞回
          if (visualRect != null) {
            animatedRect.snapTo(visualRect)
          }

          launch {
            bgAlpha.animateTo(0f, tween(OVERLAY_ANIM_DURATION, easing = FastOutSlowInEasing))
          }
          launch {
            cornerRadius.animateTo(
                source.cornerRadius,
                tween(OVERLAY_ANIM_DURATION, easing = FastOutSlowInEasing),
            )
          }
          animatedRect.animateTo(
              source.boundsInWindow,
              tween(OVERLAY_ANIM_DURATION, easing = FastOutSlowInEasing),
          )

          onDismissFinished()
        }
      }
    }

    BackHandler(enabled = !isDismissing) {
      triggerDismiss(null)
    }

    // 背景淡入淡出层
    Box(
        modifier =
            Modifier.fillMaxSize().graphicsLayer { alpha = bgAlpha.value }.background(Color.Black)
    )

    // 外框裁剪与定位层（只负责把一块 Rect 抠出来、加圆角）
    val currentRect = animatedRect.value
    val currentRadiusDp = with(density) { cornerRadius.value.toDp() }

    Box(
        modifier =
            Modifier.offset {
                  IntOffset(currentRect.left.roundToInt(), currentRect.top.roundToInt())
                }
                .size(
                    width = with(density) { currentRect.width.toDp() },
                    height = with(density) { currentRect.height.toDp() },
                )
                .clip(RoundedCornerShape(currentRadiusDp))
    ) {
      // 内容插槽：把 dismiss 触发器和当前外框给进去
      overlayContent(source, { triggerDismiss(null) }, currentRect)
    }
  }
}
