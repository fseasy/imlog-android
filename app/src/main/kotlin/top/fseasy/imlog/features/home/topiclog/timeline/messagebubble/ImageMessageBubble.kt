package top.fseasy.imlog.features.home.topiclog.timeline.messagebubble

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.fseasy.imlog.data.mapper.toActualFileOrUri
import top.fseasy.imlog.features.home.topiclog.timeline.MessageContentUiModel
import top.fseasy.imlog.features.home.topiclog.timeline.aspectRatio

@Composable
fun ImageMessageBubble(
    content: MessageContentUiModel.Image,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val imageAspectRatio = content.aspectRatio
  MediaThumbnailBubble(
      thumbnailUrl = content.thumbnailPath?.toActualFileOrUri(),
      aspectRatio = imageAspectRatio,
      onClick = onClick,
      modifier = modifier,
      overlayContent = null,
  )
}
