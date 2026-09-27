package top.fseasy.imlog.ui.components.overlaylayout

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo

/** 现代 Modifier.Node 实现： 内部通过 CompositionLocalConsumerModifierNode 安全索取 Local */
fun Modifier.overlayRegisterViewport(): Modifier = this.then(OverlayViewportElement)

private object OverlayViewportElement : ModifierNodeElement<OverlayViewportNode>() {
  override fun create(): OverlayViewportNode = OverlayViewportNode()

  override fun update(node: OverlayViewportNode) {}

  override fun hashCode(): Int = "OverlayViewportElement".hashCode()

  override fun equals(other: Any?): Boolean = other === this

  override fun InspectorInfo.inspectableProperties() {
    name = "overlayViewport"
  }
}

private class OverlayViewportNode :
  Modifier.Node(), CompositionLocalConsumerModifierNode, GlobalPositionAwareModifierNode {

  override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
    val registry = currentValueOf(LocalOverlayLayoutMetadataRegistry)
    registry.updateViewport(coordinates)
  }
}