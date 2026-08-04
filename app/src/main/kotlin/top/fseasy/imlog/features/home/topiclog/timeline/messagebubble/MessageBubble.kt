package top.fseasy.imlog.features.home.topiclog.timeline.messagebubble

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.fseasy.imlog.features.home.topiclog.timeline.AudioPlaybackState
import top.fseasy.imlog.features.home.topiclog.timeline.MessageUiModel
import java.nio.file.Path

@Composable
fun MessageBubble(
  message: MessageUiModel,
  playbackState: AudioPlaybackState,
  onVoicePlayPauseClick: (messageId: String, path: Path) -> Unit,
  onVoiceSeek: (messageId: String, positionRatio: Float) -> Unit,
  onVoiceSpeedChange: (messageId: String) -> Unit,
  onImageClick: (path: Path) -> Unit,
  onVideoClick: (path: Path) -> Unit,
  modifier: Modifier = Modifier,
) {
  val isOwn = message.isOwnMessage

  Row(
      modifier = modifier.fillMaxWidth(),
      horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
  ) {
    Card(
        modifier = Modifier.widthIn(max = 280.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isOwn) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
            ),
    ) {
      Column {
        // 根据类型分发到不同组件
        when (val content = message.content) {
          is MessageContent.Text -> {
            TextMessageBubble(text = content.text, isOwnMessage = isOwn)
          }

          is MessageContent.Image -> {
            ImageMessageBubble(
                content = content,
                onClick = { onImageClick(content.path) },
            )
          }

          is MessageContent.Video -> {
            VideoMessageBubble(
                content = content,
                onClick = { onVideoClick(content.path) },
            )
          }

          is MessageContent.Voice -> {
            VoiceMessageBubble(
                messageId = message.id,
                content = content,
                isOwnMessage = isOwn,
                isPlaying = playbackState.isPlaying(message.id),
                currentPositionMs =
                    if (playbackState.isPlaying(message.id)) playbackState.currentPositionMs
                    else 0L,
                playbackSpeed = playbackState.playbackSpeed,
                onPlayPauseClick = { onVoicePlayPauseClick(message.id, content.path) },
                onSeek = { ratio -> onVoiceSeek(message.id, ratio) },
                onSpeedChange = { onVoiceSpeedChange(message.id) },
            )
          }

          is MessageContent.GenericFile -> {
            FileMessageBubble(content = content)
          }
        }

        // 统一的时间戳样式
        Text(
            text = message.createdAt,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).align(Alignment.End),
            style = MaterialTheme.typography.labelSmall,
            color =
                if (isOwn) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
      }
    }
  }
}
