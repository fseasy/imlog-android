package top.fseasy.imlog.ui.components.zoomable

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

///** 绑定手势监听的通用 Modifier */
//fun Modifier.zoomableGestures(state: ZoomableState): Modifier =
//    this.pointerInput(state) {
//          detectTapGestures(
//              onTap = { state.onSingleTap(state) },
//              onDoubleTap = { tapOffset -> state.doubleTap(tapOffset) },
//          )
//        }
//        .pointerInput(state) {
//          state.processGestures(this)
//        }
//
///** 集中管理图形图层变换，仅在 Draw 阶段读取状态，避开重组。 同步支持 scale 动画、双指 pan 以及下拉的 X/Y 偏移。 */
//fun Modifier.zoomableContentTransform(state: ZoomableState): Modifier =
//    this.graphicsLayer {
//      val s = state.scale.value * state.dismissScaleFraction
//      scaleX = s
//      scaleY = s
//      translationX = state.offset.value.x + state.dismissDragX.value
//      translationY = state.offset.value.y + state.dismissDragY.value
//    }
