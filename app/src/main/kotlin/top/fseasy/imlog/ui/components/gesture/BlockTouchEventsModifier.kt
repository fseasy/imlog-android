package top.fseasy.imlog.ui.components.gesture

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/** 彻底吞掉该层级的所有指针事件，防止透传到底层常规列表/按钮 */
fun Modifier.blockTouchEvents(): Modifier =
    this.pointerInput(Unit) {
      awaitPointerEventScope {
        while (true) {
          val event = awaitPointerEvent(PointerEventPass.Main)
          event.changes.forEach { it.consume() }
        }
      }
    }
