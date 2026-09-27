package top.fseasy.imlog.ui.components.overlaylayout

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 🌟 现代 Modifier.Node 实现： 挂载在列表中的缩略图上，负责捕获物理坐标、等比比例、以及自动完成 Dp -> Px 转换！ */
fun Modifier.recordThumbnailBounds(
    sharedElementId: Any,
    cornerRadius: Dp = 0.dp,
    aspectRatio: Float?,
): Modifier =
    this.then(
        RecordThumbnailBoundsElement(
            sharedElementId = sharedElementId,
            cornerRadius = cornerRadius,
            aspectRatio = aspectRatio,
        )
    )

private data class RecordThumbnailBoundsElement(
    val sharedElementId: Any,
    val cornerRadius: Dp,
    val aspectRatio: Float?,
) : ModifierNodeElement<RecordThumbnailBoundsNode>() {

  override fun create(): RecordThumbnailBoundsNode =
      RecordThumbnailBoundsNode(
          sharedElementId = sharedElementId,
          cornerRadiusDp = cornerRadius,
          aspectRatio = aspectRatio,
      )

  override fun update(node: RecordThumbnailBoundsNode) {
    node.update(
        sharedElementId = sharedElementId,
        cornerRadiusDp = cornerRadius,
        aspectRatio = aspectRatio,
    )
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "recordThumbnailBounds"
    properties["sharedElementId"] = sharedElementId
    properties["cornerRadius"] = cornerRadius
    properties["aspectRatio"] = aspectRatio
  }
}

/** 🌟 核心：让 Node 自己直接实现你的 Provider 接口！ 这样它既是 Modifier.Node，又是 Registry 存储的 Provider，没有任何中间商赚差价！ */
private class RecordThumbnailBoundsNode(
    var sharedElementId: Any,
    var cornerRadiusDp: Dp,
    override var aspectRatio: Float?, // 实现接口属性
) :
    Modifier.Node(),
    OverlayLayoutThumbnailMetadataProvider, // 🌟 实现 Provider 接口！
    GlobalPositionAwareModifierNode,
    CompositionLocalConsumerModifierNode {

  // 🌟 实现接口属性：直接在 Node 内部声明，原地更新，不需要任何临时对象！
  override var coordinates: LayoutCoordinates? = null
    private set

  override var cornerRadius: Float = 0f
    private set

  override fun onAttach() {
    updateCornerRadiusPx()
    registerToRegistry(sharedElementId)
  }

  /** 坐标变化时：原地赋值！完全不重新 new 任何对象！ */
  override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
    this.coordinates = coordinates
    // 保证 attached 状态下已注册
    registerToRegistry(sharedElementId)
  }

  /** 参数更新时：原地修改属性！ */
  fun update(sharedElementId: Any, cornerRadiusDp: Dp, aspectRatio: Float?) {
    val oldKey = this.sharedElementId
    this.sharedElementId = sharedElementId
    this.cornerRadiusDp = cornerRadiusDp
    this.aspectRatio = aspectRatio

    if (oldKey != sharedElementId) {
      unregisterFromRegistry(oldKey)
      registerToRegistry(sharedElementId)
    }

    updateCornerRadiusPx()
  }

  override fun onDetach() {
    unregisterFromRegistry(sharedElementId)
    this.coordinates = null
  }

  /** LazyColumn 复用池清理 */
  override fun onReset() {
    unregisterFromRegistry(sharedElementId)
    this.coordinates = null
  }

  private fun updateCornerRadiusPx() {
    if (isAttached) {
      val density = currentValueOf(LocalDensity)
      // 🌟 自动把 Dp 原地计算成物理像素 Float！
      this.cornerRadius = with(density) { cornerRadiusDp.toPx() }
    }
  }

  private fun registerToRegistry(key: Any) {
    val registry = currentValueOf(LocalOverlayLayoutMetadataRegistry) ?: return
    // 🌟 核心：直接把 this（Node 自己）传过去，完美契合你的 register 接口！
    registry.register(key, this)
  }

  private fun unregisterFromRegistry(key: Any) {
    val registry = currentValueOf(LocalOverlayLayoutMetadataRegistry) ?: return
    // 在你的 Registry 中对应移除 key 即可
    registry.unregister(key) // 或者 registry.activeProviders.remove(key)
  }
}
