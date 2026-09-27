package top.fseasy.imlog.ui.components.overlaylayout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

@Composable
fun <T : Any> OverlayLayout(
    activeItem: T?,
    itemKey: (T) -> Any,
    modifier: Modifier = Modifier,
    onDismissFinished: () -> Unit,
    content: @Composable () -> Unit,
    overlayContent: @Composable OverlayLayoutScope.(item: T) -> Unit,
) {
  val registry = remember { OverlayLayoutThumbnailRegistry() }

  CompositionLocalProvider(LocalOverlayLayoutThumbnailRegistry provides registry) {
    Box(modifier = modifier.fillMaxSize()) {
      // 1. 底层常规 UI
      content()

      // 2. 顶层转场浮层
      activeItem?.let { item ->
        OverlayScene(
            item = item,
            itemKey = itemKey(item),
            registry = registry,
            onDismissFinished = onDismissFinished,
            overlayContent = overlayContent,
        )
      }
    }
  }
}


