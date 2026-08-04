package top.fseasy.imlog.features.home.topiclog.timeline.messagebubble

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import top.fseasy.imlog.features.home.topiclog.timeline.MessageContentUiModel

@Composable
fun ImageMessageBubble(
    content: MessageContentUiModel.Image,
    onClick: () -> Unit,
) {
  // 如果有宽高，保持动态宽高比，防止加载前跳变
  val modifier =
      if (content.width != null && content.height != null && content.height > 0) {
        Modifier.fillMaxWidth()
            .aspectRatio((content.width.toFloat() / content.height.toFloat()).coerceIn(0.5f, 2.5f))
      } else {
        Modifier.fillMaxWidth()
      }

  AsyncImage(
      model = content.path.toFile(),
      contentDescription = "Image",
      modifier = modifier.clickable { onClick() },
      contentScale = ContentScale.Crop,
  )
}
