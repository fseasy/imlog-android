package top.fseasy.imlog.ui.components.sharedtransition

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedTransitionBounds(
    key: Any?,
    enabled: Boolean = true,
    resizeMode: SharedTransitionScope.ResizeMode =
        SharedTransitionScope.ResizeMode.RemeasureToBounds,
    zIndexInOverlay: Float = 0f,
): Modifier {
  if (!enabled || key == null) return this

  val sharedTransitionScope = LocalSharedTransitionScope.current ?: return this
  val visibilityScope = LocalNavAnimatedVisibilityScope.current ?: return this

  return with(sharedTransitionScope) {
    this@sharedTransitionBounds.sharedBounds(
        rememberSharedContentState(key = key),
        animatedVisibilityScope = visibilityScope,
        resizeMode = resizeMode,
        zIndexInOverlay = zIndexInOverlay,
    )
  }
}
