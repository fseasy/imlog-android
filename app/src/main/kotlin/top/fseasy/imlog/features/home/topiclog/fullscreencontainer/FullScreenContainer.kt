package top.fseasy.imlog.features.home.topiclog.fullscreencontainer

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.fseasy.imlog.data.mapper.toActualFileOrUri
import top.fseasy.imlog.features.home.topiclog.timeline.FullScreenMessageUiModel
import top.fseasy.imlog.features.home.topiclog.timeline.MessageContentUiModel

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun FullScreenContainer(
    model: FullScreenMessageUiModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {

  Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
    when (val content = model.message.content) {
      is MessageContentUiModel.Image -> {
        ImageFullScreenViewer(
            imageUrl = model.path.toActualFileOrUri(),
            onDismiss = onClose,
            modifier = modifier,
        )
      }
      is MessageContentUiModel.Video -> VideoFullScreenPlayer()
    }
  }
}
