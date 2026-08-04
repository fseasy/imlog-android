package top.fseasy.imlog.features.home.topiclog.timeline.messagebubble

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import top.fseasy.imlog.features.home.topiclog.timeline.MessageContentUiModel

@Composable
fun VideoMessageBubble(
    content: MessageContentUiModel.Video,
    onClick: () -> Unit,
) {
  Box(
      modifier = Modifier.fillMaxWidth().height(160.dp).clickable { onClick() },
      contentAlignment = Alignment.Center,
  ) {
    // 缩略图
    AsyncImage(
        model = content.thumbnailPath?.toFile() ?: content.path.toFile(),
        contentDescription = "Video Thumbnail",
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
    )

    // 蒙层半透明播放按钮
    Box(
        modifier = Modifier.size(48.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
      Icon(
          imageVector = Icons.Default.PlayArrow,
          contentDescription = "Play",
          tint = Color.White,
          modifier = Modifier.size(28.dp),
      )
    }

    // 右下角时长标记
    Box(
        modifier =
            Modifier.align(Alignment.BottomEnd)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
      Text(
          text = formatMsToMmSs(content.durationMs),
          style = MaterialTheme.typography.labelSmall,
          color = Color.White,
      )
    }
  }
}
