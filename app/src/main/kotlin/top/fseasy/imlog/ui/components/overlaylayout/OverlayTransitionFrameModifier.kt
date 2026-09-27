package top.fseasy.imlog.ui.components.overlaylayout

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo

/** 🌟 现代 Modifier.Node 实现：全屏内容转场驱动 一个节点统一接管：视口安全裁剪 + 缩略图圆角 + 矩阵缩放平移 */
fun Modifier.overlayContentTransition(state: OverlaySceneState): Modifier =
    this.then(OverlayContentTransitionElement(state))

private data class OverlayContentTransitionElement(
    val state: OverlaySceneState,
) : ModifierNodeElement<OverlayContentTransitionNode>() {

  override fun create(): OverlayContentTransitionNode = OverlayContentTransitionNode(state)

  override fun update(node: OverlayContentTransitionNode) {
    node.update(state)
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "overlayContentTransition"
    properties["state"] = state
  }
}

private class OverlayContentTransitionNode(
    var state: OverlaySceneState,
) : Modifier.Node(), DrawModifierNode {

  // 🌟 Path 直接作为 Node 的私有成员变量！
  // 彻底消灭 remember，整个节点生命周期只创建 1 次，每帧 rewind，零 GC 损耗！
  private val roundPath = Path()

  fun update(state: OverlaySceneState) {
    if (this.state != state) {
      this.state = state
      invalidateDraw()
    }
  }

  override fun ContentDrawScope.draw() {
    // 🌟 在转场过程中执行裁剪与几何变换；Settled 交互阶段完全放开，直接原生绘制！
    if (state.transitionPhase.isTransitioning && state.isGeometryMode) {
      val clip = state.animatedClipRect.value
      val currentBounds = state.animatedRect.value
      val radius = state.cornerRadius.value
      val fitRect = state.fitRect

      // 1. 外层视口安全裁剪（保证绝不超出 TopBar / Composer）
      clipRect(
          left = clip.left,
          top = clip.top,
          right = clip.right,
          bottom = clip.bottom,
      ) {
        // 2. 内层缩略图原本的圆角裁剪
        if (radius > 0f) {
          roundPath.rewind()
          roundPath.addRoundRect(
              RoundRect(
                  rect = currentBounds,
                  cornerRadius = CornerRadius(radius),
              )
          )
          clipPath(roundPath) {
            this@draw.drawTransformedContent(fitRect, currentBounds)
          }
        } else {
          this@draw.drawTransformedContent(fitRect, currentBounds)
        }
      }
    } else {
      // Settled 阶段：不加任何裁切与矩阵，允许双指放大与手势拖拽
      drawContent()
    }
  }

  /** 🌟 几何变换：直接使用 Canvas 硬件加速矩阵将全屏内容等比映射到 currentBounds 替代原有的 graphicsLayer，零层级嵌套，逻辑内聚！ */
  private fun ContentDrawScope.drawTransformedContent(fitRect: Rect, currentBounds: Rect) {
    if (fitRect.width > 0f && fitRect.height > 0f) {
      withTransform({
        // 平移到目标位置中心
        translate(
            left = currentBounds.center.x - fitRect.center.x,
            top = currentBounds.center.y - fitRect.center.y,
        )
        // 严格等比缩放
        scale(
            scaleX = currentBounds.width / fitRect.width,
            scaleY = currentBounds.height / fitRect.height,
            pivot = fitRect.center,
        )
      }) {
        this@drawTransformedContent.drawContent()
      }
    } else {
      drawContent()
    }
  }
}
