package top.fseasy.imlog.features.home.topiclog.timeline.messagebubble

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import top.fseasy.imlog.R
import top.fseasy.imlog.data.mapper.toActualFileOrUri
import top.fseasy.imlog.domain.util.toAppMessageTimeFormat
import top.fseasy.imlog.features.home.topiclog.timeline.MessageContentUiModel
import top.fseasy.imlog.features.home.topiclog.timeline.aspectRatio

@Composable
fun VideoMessageBubble(
    content: MessageContentUiModel.Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {

  val aspectRatio = content.aspectRatio

  MediaThumbnailBubble(
      thumbnailUrl = content.thumbnailPath?.toActualFileOrUri(),
      aspectRatio = aspectRatio,
      onClick = onClick,
      modifier = modifier,
  ) {
    // Play button
    Box(
        modifier =
            Modifier.size(48.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
      Icon(
          imageVector = Icons.Default.PlayArrow,
          contentDescription = stringResource(R.string.term_media_play),
          tint = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(28.dp),
      )
    }

    // duration
    Box(
        modifier =
            Modifier.align(Alignment.BottomEnd)
                .padding(8.dp)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    RoundedCornerShape(4.dp),
                )
                .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
      Text(
          text = content.duration.toAppMessageTimeFormat(),
          style = MaterialTheme.typography.labelSmall,
      )
    }
  }
}
