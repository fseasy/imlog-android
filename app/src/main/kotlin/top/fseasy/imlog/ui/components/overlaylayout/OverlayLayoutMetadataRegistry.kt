package top.fseasy.imlog.ui.components.overlaylayout

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow

/** 惰性坐标持有者：只持有一个引用，不产生任何计算开销 */
interface OverlayLayoutThumbnailMetadataProvider {
  val coordinates: LayoutCoordinates?
  val cornerRadius: Float
  val aspectRatio: Float?
}

/**
 * Currently it holds
 * 1. thumbnail metadata: coordinates, radius, aspect
 * 2. bounds of actual viewport that contains the thumbnail (LazyColumn)
 */
@Stable
class OverlayLayoutMetadataRegistry {

  // 🌟 关键点 1：使用纯 Java/Kotlin HashMap，绝不用 mutableStateMapOf！
  // 避免在滚动更新时触发任何 Compose 快照脏标记，彻底杜绝高频重组。
  private val activeProviders = HashMap<Any, OverlayLayoutThumbnailMetadataProvider>()

  fun register(itemKey: Any, provider: OverlayLayoutThumbnailMetadataProvider) {
    activeProviders[itemKey] = provider
  }

  fun unregister(itemKey: Any) {
    activeProviders.remove(itemKey)
  }

  /**
   * the actual viewport, such as the list content. Used in [queryVisibleThumbnail]. use
   * [overlayRegisterViewport] to bind the target
   */
  private var viewportCoordinates by mutableStateOf<LayoutCoordinates?>(null)

  fun updateViewport(coordinates: LayoutCoordinates) {
    this.viewportCoordinates = coordinates
  }

  /**
   * 查询缩略图：内部自动从当前挂载的列表中提取真实可视视口！
   *
   * @param windowBounds the backup value for viewport bounds when [viewportCoordinates] is null
   */
  fun queryVisibleThumbnail(itemKey: Any, windowBounds: Rect): ThumbnailTarget? {
    val provider = activeProviders[itemKey] ?: return null
    val coordinates = provider.coordinates ?: return null
    if (!coordinates.isAttached) return null

    // 🌟 核心：优先从记录的列表组件拿真实视口！
    // 此时拿到的边界会自动卡在 TopBar 下沿和 Composer 上沿！
    val actualViewport =
        viewportCoordinates?.takeIf { it.isAttached }?.boundsInWindow()
            ?: windowBounds // 如果没配置视口，安全兜底为全屏窗口

    // 获取完整真实的物理排版尺寸（不被父级裁切）
    val fullRect = coordinates.boundsInWindow(clipBounds = false)
    if (fullRect.width <= 0f || fullRect.height <= 0f) return null

    // 物理相交判定
    if (!fullRect.overlaps(actualViewport)) return null

    // 计算实际露出的交集区域（会自动扣掉 TopBar / Composer 遮挡的部分）
    val visibleRect = fullRect.intersect(actualViewport)

    return ThumbnailTarget(
        bounds = fullRect, // 完整的 200x300
        clipBounds = visibleRect, // 露出的 200x150
        cornerRadius = provider.cornerRadius,
        aspectRatio = provider.aspectRatio,
    )
  }
}

class ThumbnailTarget(
    val bounds: Rect, // 完整物理矩形（用于图片平移和缩放）
    val clipBounds: Rect, // 初始可见遮罩矩形（用于限制起飞/降落时的可见范围）
    val cornerRadius: Float,
    val aspectRatio: Float?,
)

val LocalOverlayLayoutMetadataRegistry =
    staticCompositionLocalOf<OverlayLayoutMetadataRegistry> {
      error("Please inject LocalOverlayLayoutMetadataRegistry")
    }
