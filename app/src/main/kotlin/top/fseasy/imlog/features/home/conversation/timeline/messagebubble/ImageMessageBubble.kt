package top.fseasy.imlog.features.home.conversation.timeline.messagebubble

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.fseasy.imlog.data.mapper.toActualFileOrUri
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.features.home.conversation.timeline.MessageContentUiModel
import top.fseasy.imlog.features.home.conversation.toMemoryCacheKey
import top.fseasy.imlog.features.home.conversation.toOverlayItemKey

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
      overlayItemKey = toOverlayItemKey(messageId),
      modifier = modifier,
  )
}
