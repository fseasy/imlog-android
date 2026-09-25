package top.fseasy.imlog.ui.components.overlaylayout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

/** 惰性坐标持有者：只持有一个引用，不产生任何计算开销 */
interface OverlayLayoutThumbnailCoordinateProvider {
  val coordinates: LayoutCoordinates?
  val cornerRadius: Float
  val aspectRatio: Float?
}

@Stable
class OverlayLayoutThumbnailRegistry {
  // 🌟 关键点 1：使用纯 Java/Kotlin HashMap，绝不用 mutableStateMapOf！
  // 避免在滚动更新时触发任何 Compose 快照脏标记，彻底杜绝高频重组。
  private val activeProviders = HashMap<Any, OverlayLayoutThumbnailCoordinateProvider>()

  fun register(key: Any, provider: OverlayLayoutThumbnailCoordinateProvider) {
    activeProviders[key] = provider
  }

  fun unregister(key: Any) {
    activeProviders.remove(key)
  }

  /** 🌟 关键点 2：仅在打开或关闭的“那一瞬间”按需拉取计算！ 包含视口物理校验：即使未被回收，但滚出了屏幕边界，也判定为 null（触发微信缩放到0逻辑）。 */
  fun queryVisibleThumbnail(key: Any, windowBounds: Rect): ThumbnailTarget? {
    val provider = activeProviders[key] ?: return null
    val coords = provider.coordinates ?: return null

    // 节点必须处于挂载状态
    if (!coords.isAttached) return null

    // 在点击或退出的瞬间，才做且仅做一次矩阵换算
    val rect = coords.boundsInWindow()

    // 视口物理相交判断：气泡必须在当前屏幕可见区域内部
    val isVisibleOnScreen =
        rect.width > 0f &&
            rect.height > 0f &&
            rect.bottom > windowBounds.top &&
            rect.top < windowBounds.bottom &&
            rect.right > windowBounds.left &&
            rect.left < windowBounds.right

    return if (isVisibleOnScreen) {
      ThumbnailTarget(
          bounds = rect,
          cornerRadius = provider.cornerRadius,
          aspectRatio = provider.aspectRatio,
      )
    } else {
      null // 滚出可视区了，按 null 处理
    }
  }
}

data class ThumbnailTarget(
    val bounds: Rect,
    val cornerRadius: Float,
    val aspectRatio: Float?,
)

val LocalOverlayLayoutThumbnailRegistry =
    staticCompositionLocalOf<OverlayLayoutThumbnailRegistry> {
      error("Please inject LocalThumbnailRegistry")
    }

@Composable
fun Modifier.recordThumbnailBounds(
    key: Any,
    cornerRadius: Float = 0f,
    aspectRatio: Float? = null,
): Modifier {
  val registry = LocalOverlayLayoutThumbnailRegistry.current

  val provider =
      remember(key) {
        object : OverlayLayoutThumbnailCoordinateProvider {
          override var coordinates: LayoutCoordinates? = null
          override var cornerRadius: Float = cornerRadius
          override var aspectRatio: Float? = aspectRatio
        }
      }
  // Update value when those values changed while key is the same
  SideEffect {
    provider.cornerRadius = cornerRadius
    provider.aspectRatio = aspectRatio
  }

  DisposableEffect(key, registry) {
    registry.register(key, provider)
    onDispose {
      // 🌟 解决问题 2：离开屏幕立刻回收，Map 里永远只有几条数据
      registry.unregister(key)
    }
  }

  return this.onGloballyPositioned { coords ->
    // 🌟 解决问题 1：滚动时只更新引用，耗时 0.001ms，绝不计算 boundsInWindow()，绝不写 State！
    provider.coordinates = coords
  }
}
