package top.fseasy.imlog.features.home.topiclog.fullscreencontainer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.fseasy.imlog.R
import top.fseasy.imlog.ui.components.gesture.DismissibleBox
import top.fseasy.imlog.ui.components.gesture.ZoomableBox
import top.fseasy.imlog.ui.components.gesture.rememberDismissState
import top.fseasy.imlog.ui.components.gesture.rememberTransformHandoverChain
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
  val zoomState = rememberZoomableState(overlayTransitionElementId)
  val dismissState = rememberDismissState(overlayTransitionElementId)

  val dismissFromVisualRect =
      remember(overlayTransitionElementId) {
        { this.dismiss(overlayTransitionElementId) }
      }

  val coroutineScope = rememberCoroutineScope()
  var zoomJob by remember { mutableStateOf<Job?>(null) }

  this.configureBackground(
      onTap = dismissFromVisualRect,
      onDoubleTap = { offset ->
        // only reset room. this offset maybe weird as it's out of the image, so don't zoom in
        if (zoomState.isZoomed) {
          zoomJob?.cancel()
          zoomJob = coroutineScope.launch { zoomState.toggleZoom(offset) }
        }
      },
  )

  val context = LocalContext.current
  val imageRequest =
      remember(imageUrl, thumbnailCacheKey) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .placeholderMemoryCacheKey(thumbnailCacheKey)
            .crossfade(200)
            .build()
      }

  val handoverChain = rememberTransformHandoverChain(zoomState, dismissState)

  DismissibleBox(
      state = dismissState,
      enabled = { !zoomState.isZoomed },
      drawBackground = false,
      onDismissRequest = dismissFromVisualRect,
      animateSwipeDismiss = false,
      modifier =
          modifier
              .fillMaxSize()
              .overlayInteractiveTarget(
                  itemKey = overlayTransitionElementId,
                  backgroundAlphaProvider = { dismissState.backgroundAlpha },
                  dismissTransformHandoverProvider = { handoverChain },
              ),
  ) {
    ZoomableBox(
        state = zoomState,
        coroutineScope = coroutineScope,
        onSingleTap = dismissFromVisualRect,
        modifier = Modifier.fillMaxSize(),
    ) {
      AsyncImage(
          model = imageRequest,
          contentDescription = stringResource(R.string.term_full_screen_image),
          // 2. 由 Fit 自动在全屏空间内保持长宽比居中显示，不要加 aspectRatio！
          contentScale = ContentScale.Fit,
          modifier = Modifier.fillMaxSize(),
      )
    }
  }
}
