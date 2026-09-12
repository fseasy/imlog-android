package top.fseasy.imlog.ui.components.contextmenu

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

/**
 * Click Modifier that mainly for popup context menu when long click. If you want to show context
 * menu when long click, put the menu showing function to the [onLongClickWithPosition].
 *
 * @param onClick function for single click(no context menu). you can bind the target by lambda
 * @param onLongClickWithPosition function for long click, with a param injection of click position
 *   (absolute in the Window).
 *
 *   Mostly, use a lambda that capture the target and then call [ContextMenuState.show]
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.contextMenuClickable(
    onClick: () -> Unit = {},
    onLongClickWithPosition: (windowOffset: IntOffset) -> Unit,
): Modifier =
    this.then(
        Modifier.composed {
          var itemPositionInWindow by remember { mutableStateOf(Offset.Zero) }
          var touchPositionInItem by remember { mutableStateOf(Offset.Zero) }

          Modifier.onGloballyPositioned { coordinates ->
                // 捕获 Item 在屏幕窗口内的绝对位置
                itemPositionInWindow = coordinates.positionInWindow()
              }
              .pointerInput(Unit) {
                // 静默收集触摸坐标，不拦截手势、不影响滑动与点击
                awaitEachGesture {
                  val down = awaitFirstDown(requireUnconsumed = false)
                  touchPositionInItem = down.position
                }
              }
              .combinedClickable(
                  onClick = onClick,
                  onLongClick = {
                    val globalOffset =
                        IntOffset(
                            (itemPositionInWindow.x + touchPositionInItem.x).roundToInt(),
                            (itemPositionInWindow.y + touchPositionInItem.y).roundToInt(),
                        )
                    onLongClickWithPosition(globalOffset)
                  },
              )
        }
    )
