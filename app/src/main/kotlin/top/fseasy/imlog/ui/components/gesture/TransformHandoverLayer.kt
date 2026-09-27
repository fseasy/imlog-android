package top.fseasy.imlog.ui.components.gesture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/** 可参与 Overlay 退场交接的手势形变层协议 */
interface TransformHandoverLayer {
  /** 将输入的 Rect 经过本层的 Scale / Offset 变换，映射出本层的视觉 Rect */
  fun mapRect(input: Rect): Rect

  /** 归一化重置本层手势状态（擦除脏数据，交出控制权） */
  fun reset()
}

fun Rect.applyTransform(scale: Float, offset: Offset): Rect {
  if (scale == 1f && offset == Offset.Zero) return this
  val newCenter = this.center + offset
  val newW = this.width * scale
  val newH = this.height * scale
  return Rect(
      left = newCenter.x - newW / 2f,
      top = newCenter.y - newH / 2f,
      right = newCenter.x + newW / 2f,
      bottom = newCenter.y + newH / 2f,
  )
}

/** 手势交接链（按由内到外的顺序组合） */
class TransformHandoverChain(
    private val layers: List<TransformHandoverLayer>,
) {
  /** 🌟 核心：函数链式折叠（Fold）计算 + 批量原子重置 */
  fun captureVisualRectAndReset(baseRect: Rect): Rect {
    // 1. 从内到外链式流转变换：Layer1(fitRect) -> Layer2(result1) -> ...
    val finalVisualRect =
        layers.fold(baseRect) { currentRect, layer ->
          layer.mapRect(currentRect)
        }

    // 2. 采样完成后，立即把整条链上的所有状态全部洗净
    layers.forEach { it.reset() }

    return finalVisualRect
  }
}

/**
 * 构建 TransformHandoverChain， 它会组层叠加 transform (scale + offset), 并 reset 各个 layer 内部的 scale + offset
 * 用做最终的 transform 交接。一定要注意此函数的副作用，它会 reset scale & offset
 *
 * @param layers 参数顺序要按照 layer 由内往外排布，也就是最内层的 layer 放第一个
 */
@Composable
fun rememberTransformHandoverChain(
    vararg layers: TransformHandoverLayer,
): TransformHandoverChain {
  return remember(layers) { TransformHandoverChain(layers.toList()) }
}
