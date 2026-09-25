package top.fseasy.imlog.features.home.topiclog.timeline.messagebubble

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.fseasy.imlog.data.mapper.toActualFileOrUri
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.features.home.topiclog.timeline.MessageContentUiModel
import top.fseasy.imlog.features.home.topiclog.toMemoryCacheKey
import top.fseasy.imlog.features.home.topiclog.toSharedTransitionElementId

@Composable
fun ImageMessageBubble(
    messageId: MessageId,
    content: MessageContentUiModel.Image,
    modifier: Modifier = Modifier,
) {
  MediaThumbnailBubble(
      url = content.thumbnailPath?.toActualFileOrUri(),
      widthPx = content.width,
      heightPx = content.height,
      imageMemoryCacheKey = toMemoryCacheKey(messageId),
      sharedElementId = toSharedTransitionElementId(messageId),
      modifier = modifier,
      overlayContent = null,
  )
}
