package top.fseasy.imlog.ui.components.contextmenu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset

/**
 * Genric State for ContextMenu.
 *
 * Mostly, it should be use with [contextMenuClickable]
 */
class ContextMenuState<T> {
  var target by mutableStateOf<T?>(null)
    private set

  var position by mutableStateOf(IntOffset.Zero)
    private set

  val isVisible: Boolean
    get() = target != null

  fun show(target: T, position: IntOffset) {
    this.target = target
    this.position = position
  }

  fun dismiss() {
    this.target = null
  }
}

@Composable
fun <T> rememberContextMenuState(): ContextMenuState<T> = remember { ContextMenuState() }
