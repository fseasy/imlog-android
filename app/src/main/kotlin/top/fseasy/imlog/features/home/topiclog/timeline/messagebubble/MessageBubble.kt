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
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.features.home.topiclog.timeline.AudioPlaybackState
import top.fseasy.imlog.features.home.topiclog.timeline.MessageContentUiModel
import top.fseasy.imlog.features.home.topiclog.timeline.MessageSenderUiModel
import top.fseasy.imlog.features.home.topiclog.timeline.MessageUiModel
import java.nio.file.Path

@Composable
fun MessageBubble(
    message: MessageUiModel,
    audioPlaybackStateHolder: State<AudioPlaybackState>,
    audioPlayPositionHolder: State<kotlin.time.Duration>,
    inactivePlayPositionGetter: (MessageId) -> kotlin.time.Duration,
    onToggleAudioPlay: (message: MessageUiModel) -> Unit,
    onSeekAudio: (message: MessageUiModel, positionRatio: Float) -> Unit,
    onChangeAudioPlaySpeed: (MessageId) -> Unit,
    onShowImage: (path: Path) -> Unit,
    onShowVideo: (path: Path) -> Unit,
    modifier: Modifier = Modifier,
) {
  val isOwn = message.sender is MessageSenderUiModel.Own

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
        when (val content = message.content) {
          is MessageContentUiModel.Text -> {
            TextMessageBubble(text = content.text, isOwnMessage = isOwn)
          }

          is MessageContentUiModel.Image -> {
            ImageMessageBubble(
                content = content,
                onClick = { onShowImage(content.path) },
            )
          }

          is MessageContentUiModel.Video -> {
            VideoMessageBubble(
                content = content,
                onClick = { onShowVideo(content.path) },
            )
          }

          is MessageContentUiModel.Voice -> {
            // When target change, those will be re-composition.
            val audioPlaybackState = audioPlaybackStateHolder.value
            val isActive = audioPlaybackState.isThisMessageActive(message.id)
            val currentPlaybackState =
                if (isActive) {
                  audioPlaybackState
                } else {
                  AudioPlaybackState(duration = content.duration)
                }
            // cache value will be recorded before switching to next one
            val cachedPlayPosition = inactivePlayPositionGetter(message.id)
            VoiceMessageBubble(
                messageId = message.id,
                sender = message.sender,
                content = content,
                isOwnMessage = isOwn,
                playbackState = currentPlaybackState,
                activePlayPositionHolder = audioPlayPositionHolder,
                inactivePlayPosition = cachedPlayPosition,
                onTogglePlay = { onToggleAudioPlay(message) },
                onSeek = { ratio -> onSeekAudio(message, ratio) },
                onSpeedChange = { onChangeAudioPlaySpeed(message.id) },
            )
          }
          is MessageContentUiModel.Audio -> TODO()

          is MessageContentUiModel.GenericFile -> {
            FileMessageBubble(content = content)
          }
        }

        Text(
            text = message.formatedCreatedAt,
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
