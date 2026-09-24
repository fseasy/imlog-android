package top.fseasy.imlog.ui.components.sharedtransition

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Used for shared element transition */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedTransitionElement(
  key: Any?,
  enabled: Boolean = true,
  zIndexInOverlay: Float = 0f,
  boundsTransform: BoundsTransform = SharedTransitionDefaults.BoundsTransform,
): Modifier {
  // 1. 前置守卫：如果未启用或 key 为空，直接原样返回
  if (!enabled || key == null) return this

  // 2. 获取 CompositionLocal，若任意一个为 null 则降级为无动效
  val sharedTransitionScope = LocalSharedTransitionScope.current ?: return this
  val visibilityScope = LocalNavAnimatedVisibilityScope.current ?: return this

  // 3. 在作用域内构建 sharedElement Modifier
  return with(sharedTransitionScope) {
    this@sharedTransitionElement.sharedElement(
        rememberSharedContentState(key = key),
        animatedVisibilityScope = visibilityScope,
        boundsTransform = boundsTransform,
        zIndexInOverlay = zIndexInOverlay,
    )
  }
}