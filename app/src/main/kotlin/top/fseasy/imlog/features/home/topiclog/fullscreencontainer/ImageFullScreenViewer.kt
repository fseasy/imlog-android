package top.fseasy.imlog.features.home.topiclog.fullscreencontainer

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.cancellable
import top.fseasy.imlog.R
import top.fseasy.imlog.ui.components.sharedtransition.LocalSharedImageController
import top.fseasy.imlog.ui.components.sharedtransition.OverlayPhase
import top.fseasy.imlog.ui.components.sharedtransition.sharedFullScreen
import top.fseasy.imlog.ui.components.zoomable.ZoomableState
import top.fseasy.imlog.ui.components.zoomable.rememberZoomableState
import top.fseasy.imlog.ui.components.zoomable.zoomableContentOffset
import top.fseasy.imlog.ui.components.zoomable.zoomableContentScale
import top.fseasy.imlog.ui.components.zoomable.zoomableGesture

/**
 * We need the preloaded thumbnail to smooth the shared-element transition. Or the animation will
 * gitter.
 */
@Composable
fun ImageFullScreenViewer(
    imageUrl: Any,
    aspectRatio: Float,
    thumbnailCacheKey: String,
    sharedElementId: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    state: ZoomableState =
        rememberZoomableState(onDismiss = onDismissRequest, animateDismiss = false),
) {
  val controller = LocalSharedImageController.current
  val coroutineScope = rememberCoroutineScope()

  PredictiveBackHandler { progressFlow ->
    try {
      progressFlow.cancellable().collect { backEvent ->
        controller?.snapAlpha(1f - backEvent.progress)
        state.updatePredictiveBackProgress(backEvent.progress)
      }
      onDismissRequest()
    } catch (e: CancellationException) {
      controller?.snapAlpha(1f)
      state.resetBackState()
      throw e
    }
  }

  LaunchedEffect(state.backgroundAlpha) {
    if (controller?.phase is OverlayPhase.Showing) {
      controller.snapAlpha(state.backgroundAlpha)
    }
  }

  // Background color. Make it independent of the Shared Transition element
  // Or it will show an alpha gitter for transition in drag-down
  Box(
      modifier =
          Modifier.fillMaxSize()
              .graphicsLayer { alpha = controller?.bgAlpha?.value ?: 1f }
              .background(Color.Black)
  )

  Box(
      modifier =
          modifier
              .fillMaxSize()
              // bind zoomable gesture
              .zoomableGesture(state),
      contentAlignment = Alignment.Center,
  ) {
    AsyncImage(
        model =
            ImageRequest.Builder(LocalContext.current)
                .data(imageUrl)
                .placeholderMemoryCacheKey(thumbnailCacheKey)
                .crossfade(200)
                .build(),
        contentDescription = stringResource(R.string.term_full_screen_image),
        contentScale = ContentScale.Fit,
        modifier =
            Modifier.aspectRatio(aspectRatio)
                .fillMaxSize()
                // trials and trials, We still didn't find the perfect transforming effects.
                // We only know, must set Offset before the shared transition.
                .zoomableContentOffset(state)
                .sharedFullScreen(sharedElementId)
                .zoomableContentScale(state),
    )
  }
}
