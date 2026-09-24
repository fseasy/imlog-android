package top.fseasy.imlog.ui.components.sharedtransition

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

// ==============================================================================
// 1. 核心控制器 & CompositionLocal (单一信任源)
// ==============================================================================

sealed interface OverlayPhase {
  data object Idle : OverlayPhase

  data class Showing(val key: Any) : OverlayPhase

  data class Dismissing(val key: Any) : OverlayPhase
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Stable
class SharedImageController(val sharedScope: SharedTransitionScope) {
  var phase by mutableStateOf<OverlayPhase>(OverlayPhase.Idle)
    internal set

  val bgAlpha = Animatable(0f)

  /** 打开全屏（合并了设置 Showing 和背景淡入） */
  suspend fun show(key: Any) {
    phase = OverlayPhase.Showing(key)
    bgAlpha.animateTo(
        targetValue = 1f,
        animationSpec = tween(OVERLAY_ANIMATION_DURATION, easing = FastOutSlowInEasing),
    )
  }

  /** 退出全屏（合并了触发飞回、背景淡出、以及动画完成后的状态归位） */
  suspend fun dismiss() {
    val current = phase
    if (current !is OverlayPhase.Showing) {
      return
    }
    // 1. 立刻触发一进一退（缩略图变 true，全屏变 false）
    phase = OverlayPhase.Dismissing(current.key)
    coroutineScope {
      launch {
        bgAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(OVERLAY_ANIMATION_DURATION, easing = FastOutSlowInEasing),
        )
      }
      // 等 SharedTransition 真正跑完（比单纯 delay 更可靠）
      withTimeoutOrNull((OVERLAY_ANIMATION_DURATION + 80L).milliseconds) {
        // 先等到 transition 启动
        snapshotFlow { sharedScope.isTransitionActive }.first { it }
        // 再等到它结束
        snapshotFlow { sharedScope.isTransitionActive }.first { !it }
      }
    }
    // 3. 安全重置为 Idle
    if ((phase as? OverlayPhase.Dismissing)?.key == current.key) {
      phase = OverlayPhase.Idle
    }
  }

  // 保留给手势拖拽使用（如 PredictiveBack 或下拉阻尼）
  suspend fun snapAlpha(alpha: Float) {
    bgAlpha.snapTo(alpha.coerceIn(0f, 1f))
  }
}

val LocalSharedImageController = staticCompositionLocalOf<SharedImageController?> { null }

private const val OVERLAY_ANIMATION_DURATION = 260 // 黄金时长 260ms

val OverlayBoundsTransform = BoundsTransform { _, _ ->
  tween(
      durationMillis = OVERLAY_ANIMATION_DURATION,
      easing = FastOutSlowInEasing, // 先快后慢，视觉最舒适
  )
}

// ==============================================================================
// 2. Modifier 扩展
// ==============================================================================

/** 挂载在【气泡端】的缩略图上。 内部会自动感知全局 Controller，若当前项处于全屏态，气泡自动隐藏让位并产生共享飞渡。 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedThumbnail(
    key: Any?,
    shape: Shape = RoundedCornerShape(12.dp),
    boundsTransform: BoundsTransform = OverlayBoundsTransform,
): Modifier {
  if (key == null) return this
  val controller = LocalSharedImageController.current ?: return this

  return with(controller.sharedScope) {
    this@sharedThumbnail.sharedElementWithCallerManagedVisibility(
        sharedContentState = rememberSharedContentState(key = key),
        visible =
            when (val phase = controller.phase) {
              // if not current showing, visible = true; else false
              is OverlayPhase.Showing -> phase.key != key
              else -> true
            },
        boundsTransform = boundsTransform,
        clipInOverlayDuringTransition = OverlayClip(shape),
    )
  }
}

/** 挂载在【全屏端】的大图上。 退出时自动响应飞回动画，将大图沿原轨迹缩减回气泡位置。 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedFullScreen(
    key: Any?,
    shape: Shape = RectangleShape,
    boundsTransform: BoundsTransform = OverlayBoundsTransform,
): Modifier {
  if (key == null) return this
  val controller = LocalSharedImageController.current ?: return this

  return with(controller.sharedScope) {
    this@sharedFullScreen.sharedElementWithCallerManagedVisibility(
        sharedContentState = rememberSharedContentState(key = key),
        visible = controller.phase is OverlayPhase.Showing,
        boundsTransform = boundsTransform,
        clipInOverlayDuringTransition = OverlayClip(shape),
    )
  }
}

// ==============================================================================
// 3. 通用全屏大图容器
// ==============================================================================

/**
 * 微信风格的图片查看器外层容器：
 * - content 底页常驻，绝不退出、不销毁；
 * - 纯黑背景只淡入淡出，绝对没有 0x0 异常尺寸形变；
 * - 退出时全屏保持渲染，等待大图精确飞回气泡后自动清理销毁。
 *
 * @param onDismissFinished action when dismiss animation finished
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun <T : Any> SharedTransitionImageOverlayLayout(
    activeItem: T?,
    itemKey: (T) -> Any,
    modifier: Modifier = Modifier,
    onDismissFinished: (() -> Unit)? = null,
    content: @Composable () -> Unit,
    overlay: @Composable (item: T) -> Unit,
) {
  SharedTransitionLayout(modifier = modifier) {
    val controller = remember(this) { SharedImageController(this) }

    // 缓存飞回过程中的数据，防止外部 activeItem = null 瞬间导致全屏空白
    var renderedItem by remember { mutableStateOf(activeItem) }

    // 统一生命周期驱动状态机
    LaunchedEffect(activeItem) {
      if (activeItem != null) {
        // 进入态：记录展示
        renderedItem = activeItem
        controller.show(itemKey(activeItem))
      } else if (renderedItem != null) {
        // 退出态：触发飞回
        controller.dismiss()
        // 此刻动画结束，销毁并回调
        renderedItem = null
        onDismissFinished?.invoke()
      }
    }

    CompositionLocalProvider(LocalSharedImageController provides controller) {
      Box(modifier = Modifier.fillMaxSize()) {
        // 1. 底层主体
        content()

        // 2. 顶层浮层：仅在展示或飞回期间渲染
        renderedItem?.let { item -> overlay(item) }
      }
    }
  }
}
