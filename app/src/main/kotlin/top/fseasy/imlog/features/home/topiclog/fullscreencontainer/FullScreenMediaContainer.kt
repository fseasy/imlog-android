package top.fseasy.imlog.features.home.topiclog.fullscreencontainer

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun FullScreenMediaContainer(
    mediaInput: MediaInput,
    mediaPlayerStateHolder: MediaPlayerStateHolder,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {

  Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
    // 核心：根据 MediaType 决定内部分发渲染什么组件
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
