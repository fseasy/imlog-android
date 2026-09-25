package top.fseasy.imlog.features.home.topiclog.fullscreencontainer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import top.fseasy.imlog.R
import top.fseasy.imlog.ui.components.gesture.DismissibleBox
import top.fseasy.imlog.ui.components.gesture.ZoomableBox
import top.fseasy.imlog.ui.components.gesture.computeVisualRect
import top.fseasy.imlog.ui.components.gesture.rememberDismissState
import top.fseasy.imlog.ui.components.gesture.rememberZoomableState
import top.fseasy.imlog.ui.components.overlaylayout.OverlayLayoutScope

/**
 * We need the preloaded thumbnail to smooth the shared-element transition. Or the animation will
 * gitter.
 */
@Composable
fun OverlayLayoutScope.ImageFullScreenViewer(
    overlayTransitionElementId: String,
    imageUrl: Any,
    thumbnailCacheKey: String,
    modifier: Modifier = Modifier,
) {
  //
  //  val zoomState2: ZoomableState =
  //      rememberZoomableState2(
  //          onDismissRequest = {
  //            onDismissWithTransition(this::computeVisualRect)
  //          },
  //          animateDismiss = false,
  //      )

  //  Box(
  //      modifier =
  //        Modifier.fillMaxSize()
  //            // 1. 手势挂载在外层全屏 Box 上！
  //            // 保证用户在屏幕边缘、上下黑色留白区下滑，都能流畅触发下拉退出或单击！
  //            .zoomableGestures(zoomState2),
  //      contentAlignment = Alignment.Center,
  //  ) {
  //    AsyncImage(
  //        model =
  //          ImageRequest.Builder(LocalContext.current)
  //              .data(imageUrl)
  //              .placeholderMemoryCacheKey(thumbnailCacheKey)
  //              .crossfade(200)
  //              .build(),
  //        contentDescription = stringResource(R.string.term_full_screen_image),
  //        // 2. 由 Fit 自动在全屏空间内保持长宽比居中显示，不要加 aspectRatio！
  //        contentScale = ContentScale.Fit,
  //        modifier =
  //          Modifier.fillMaxSize()
  //              // 3. 视觉变换挂在图片自身上（缩放和平移）
  //              .zoomableContentTransform(zoomState2),
  //    )
  //  }

  val zoomState = rememberZoomableState()
  val dismissState = rememberDismissState()

  val dismissFromVisualRect =
      remember(zoomState, dismissState) {
        {
          this.dismiss(overlayTransitionElementId) { baseRect ->
            computeVisualRect(baseRect, zoomableState = zoomState, dismissState = dismissState)
          }
        }
      }
  this.bindBackgroundAlpha { dismissState.backgroundAlpha }

  DismissibleBox(
      state = dismissState,
      enabled = { !zoomState.isZoomed }, // 👈 传 Lambda：MediaViewerScreen 彻底脱离重组！
      drawBackground = false,
      onDismissRequest = dismissFromVisualRect,
      animateSwipeDismiss = false,
      modifier = modifier.fillMaxSize(),
  ) {
    ZoomableBox(
        state = zoomState,
        onSingleTap = dismissFromVisualRect,
        modifier = Modifier.fillMaxSize(),
    ) {
      AsyncImage(
          model =
              ImageRequest.Builder(LocalContext.current)
                  .data(imageUrl)
                  .placeholderMemoryCacheKey(thumbnailCacheKey)
                  .crossfade(200)
                  .build(),
          contentDescription = stringResource(R.string.term_full_screen_image),
          // 2. 由 Fit 自动在全屏空间内保持长宽比居中显示，不要加 aspectRatio！
          contentScale = ContentScale.Fit,
          modifier = Modifier.fillMaxSize(),
      )
    }
  }
}
