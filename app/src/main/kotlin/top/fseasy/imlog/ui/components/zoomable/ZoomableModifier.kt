package top.fseasy.imlog.ui.components.zoomable

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

/** 将手势驱动逻辑与图形变换解耦成单一的 Modifier */
fun Modifier.zoomableGesture(state: ZoomableState): Modifier =
    this.onSizeChanged { state.containerSize = it }
        // 监听单击与双击手势
        .pointerInput(state) {
          detectTapGestures(
              onTap = { state.onSingleTap() },
              onDoubleTap = { tapOffset -> state.onDoubleTap(tapOffset) },
          )
        }
        // 监听拖拽、缩放统一事件流
        .pointerInput(state) {
          state.processGestures(this)
        }

/**
 * 具有空间感知的通用 Zoomable 内容变换：
 * 1. 位移放在 Placement 阶段（零重组，但更新真实 LayoutCoordinates，任何外部模块皆可感知）；
 * 2. 缩放放在 Draw 阶段（零重组，零重新测量，保证手势缩放极致流畅）。
 */
fun Modifier.zoomableContent(state: ZoomableState): Modifier =
    this.zoomableContentOffset(state).zoomableContentScale(state)

fun Modifier.zoomableContentOffset(state: ZoomableState): Modifier =
    this.offset {
      // 放在 Placement 阶段，更新物理坐标系
      IntOffset(
          x = state.offset.value.x.roundToInt(),
          y = (state.offset.value.y + state.dismissDragY.value).roundToInt(),
      )
    }

fun Modifier.zoomableContentScale(state: ZoomableState): Modifier =
    this.graphicsLayer {
      // 纯绘制阶段处理尺寸缩放
      val dismissScale = state.dismissScaleFraction
      scaleX = state.scale.value * dismissScale
      scaleY = state.scale.value * dismissScale
    }
