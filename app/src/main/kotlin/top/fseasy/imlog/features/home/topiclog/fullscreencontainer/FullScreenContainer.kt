package top.fseasy.imlog.features.home.topiclog.fullscreencontainer

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun FullScreenContainer(
  sharedTransitionScope: SharedTransitionScope,
  animatedVisibilityScope: AnimatedVisibilityScope,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {

  Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
    when (mediaInput.type) {
      MediaType.IMAGE -> {
        FullScreenImageContent(
            mediaInput = mediaInput,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
        )
      }
      MediaType.VIDEO -> {
        FullScreenVideoContent(
            mediaInput = mediaInput,
            mediaPlayerStateHolder = mediaPlayerStateHolder,
            areControlsVisible = areControlsVisible,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            onControlsVisibilityChange = { areControlsVisible = it },
        )
      }
    }
  }
}
