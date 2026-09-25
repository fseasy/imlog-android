package top.fseasy.imlog.ui.components.overlaylayout

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun <T : Any> OverlayLayout(
    activeItem: T?,
    itemKey: (T) -> Any,
    modifier: Modifier = Modifier,
    onDismissFinished: () -> Unit,
    content: @Composable () -> Unit,
    overlayContent: @Composable OverlayLayoutScope.(item: T) -> Unit,
) {
  val registry = remember { OverlayLayoutThumbnailRegistry() }

  CompositionLocalProvider(LocalOverlayLayoutThumbnailRegistry provides registry) {
    Box(modifier = modifier.fillMaxSize()) {
      // 1. 底层常规 UI
      content()

      // 2. 顶层转场浮层
      activeItem?.let { item ->
        OverlayHostStage(
            item = item,
            itemKey = itemKey(item),
            registry = registry,
            onDismissFinished = onDismissFinished,
            overlayContent = overlayContent,
        )
      }
    }
  }
}

private val RectVectorConverter =
    TwoWayConverter<Rect, AnimationVector4D>(
        convertToVector = { AnimationVector4D(it.left, it.top, it.right, it.bottom) },
        convertFromVector = { Rect(it.v1, it.v2, it.v3, it.v4) },
    )

private const val TRANSITION_DURATION = 260

/** 转场大舞台生命周期状态机 */
enum class OverlayStage {
  Entering, // 入场展开动效中
  Entered, // 稳定浏览交互中
  Dismissing; // 退场飞回动效中

  val isTransitioning: Boolean
    get() = this != Entered
}

@Composable
private fun <T : Any> OverlayHostStage(
    item: T,
    itemKey: Any,
    registry: OverlayLayoutThumbnailRegistry,
    onDismissFinished: () -> Unit,
    overlayContent: @Composable OverlayLayoutScope.(item: T) -> Unit,
) {
  val density = LocalDensity.current
  val coroutineScope = rememberCoroutineScope()

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val screenW = constraints.maxWidth.toFloat()
    val screenH = constraints.maxHeight.toFloat()
    val windowBounds = Rect(0f, 0f, screenW, screenH)
    val screenCenter = Offset(screenW / 2f, screenH / 2f)

    val initialTarget = remember { registry.queryVisibleThumbnail(itemKey, windowBounds) }
    val isGeometryMode = initialTarget != null

    val fitRect =
        remember(screenW, screenH, initialTarget?.aspectRatio) {
          val ratio = initialTarget?.aspectRatio ?: (screenW / screenH)
          val containerRatio = screenW / screenH
          val w = if (ratio > containerRatio) screenW else screenH * ratio
          val h = if (ratio > containerRatio) screenW / ratio else screenH
          Rect(
              screenCenter.x - w / 2f,
              screenCenter.y - h / 2f,
              screenCenter.x + w / 2f,
              screenCenter.y + h / 2f,
          )
        }

    val startRect = initialTarget?.bounds ?: fitRect
    val animatedRect = remember { Animatable(startRect, RectVectorConverter) }
    val bgAlpha = remember { Animatable(0f) }
    val cornerRadius = remember { Animatable(initialTarget?.cornerRadius ?: 0f) }

    // 🌟 统一生命周期状态机
    var stage by remember { mutableStateOf(OverlayStage.Entering) }
    var contentAlphaProvider by remember { mutableStateOf<(() -> Float)?>(null) }

    val triggerDismiss: (currentItemKey: Any, visualRect: Rect?) -> Unit =
        { currentKey, visualRect ->
          if (stage != OverlayStage.Dismissing) {
            stage = OverlayStage.Dismissing
            coroutineScope.launch {
              // 🌟 衔接当前手势拖拽产生的实际 Alpha，彻底杜绝闪烁跳变
              val currentVisualAlpha = contentAlphaProvider?.invoke() ?: bgAlpha.value
              bgAlpha.snapTo(currentVisualAlpha)

              val startVRect = visualRect ?: if (isGeometryMode) fitRect else animatedRect.value
              animatedRect.snapTo(startVRect)

              val targetThumbnail = registry.queryVisibleThumbnail(currentKey, windowBounds)
              val finalRect =
                  targetThumbnail?.bounds
                      ?: Rect(screenCenter.x, screenCenter.y, screenCenter.x, screenCenter.y)
              val finalRadius = targetThumbnail?.cornerRadius ?: 0f

              coroutineScope {
                launch {
                  bgAlpha.animateTo(0f, tween(TRANSITION_DURATION, easing = FastOutSlowInEasing))
                }
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
        }

    val overlayLayoutScope =
        remember(fitRect) {
          object : OverlayLayoutScope {
            override fun dismiss(itemKey: Any, transform: ((baseRect: Rect) -> Rect)?) {
              val visualRect = transform?.invoke(fitRect)
              triggerDismiss(itemKey, visualRect)
            }

            override fun bindBackgroundAlpha(alphaProvider: () -> Float) {
              contentAlphaProvider = alphaProvider
            }
          }
        }

    // 入场动效：并行启动并在全部结束后推进到 Entered
    LaunchedEffect(Unit) {
      coroutineScope {
        launch {
          bgAlpha.animateTo(1f, tween(TRANSITION_DURATION, easing = FastOutSlowInEasing))
        }
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
      // 防御性流转：若入场期间未被提前打断返回，则进入稳定交互态
      if (stage == OverlayStage.Entering) {
        stage = OverlayStage.Entered
      }
    }

    BackHandler(enabled = stage != OverlayStage.Dismissing) {
      triggerDismiss(itemKey, null)
    }

    // 1. 背景层：生命周期三态决策
    Box(
        modifier =
            Modifier.fillMaxSize()
                .graphicsLayer {
                  alpha =
                      if (stage == OverlayStage.Entered) {
                        contentAlphaProvider?.invoke() ?: 1f
                      } else {
                        bgAlpha.value
                      }
                }
                .background(Color.Black),
    )

    // 2. 几何裁剪外框
    val currentRect =
        if (stage == OverlayStage.Entered && isGeometryMode) fitRect else animatedRect.value
    val currentRadiusDp = with(density) { cornerRadius.value.toDp() }

    Box(
        modifier =
            Modifier.offset {
                  IntOffset(currentRect.left.roundToInt(), currentRect.top.roundToInt())
                }
                .size(
                    width = with(density) { currentRect.width.coerceAtLeast(0f).toDp() },
                    height = with(density) { currentRect.height.coerceAtLeast(0f).toDp() },
                )
//                // 🌟 核心：转场时裁剪圆角；稳定交互期去掉 clip，手势拖拽不切边
//                .then(
//                    if (stage.isTransitioning) Modifier.clip(RoundedCornerShape(currentRadiusDp))
//                    else Modifier
//                )
        ,
        contentAlignment = Alignment.Center,
    ) {
      overlayLayoutScope.overlayContent(item)
    }
  }
}
