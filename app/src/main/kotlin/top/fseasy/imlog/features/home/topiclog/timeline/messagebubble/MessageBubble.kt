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
import top.fseasy.imlog.data.util.MediaPlaybackState
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.features.home.topiclog.MediaPlaybackStateAndAction
import top.fseasy.imlog.features.home.topiclog.timeline.MessageContentUiModel
import top.fseasy.imlog.features.home.topiclog.timeline.MessageSenderUiModel
import top.fseasy.imlog.features.home.topiclog.timeline.MessageUiModel
import top.fseasy.imlog.features.home.topiclog.toMediaInputId
import kotlin.time.Duration

@Composable
fun MessageBubble(
    message: MessageUiModel,
    mediaPlaybackStateAndAction: MediaPlaybackStateAndAction,
    onShowImage: (MessageUiModel) -> Unit,
    onShowVideo: (MessageUiModel) -> Unit,
    onOpenFile: (MessageUiModel) -> Unit,
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
                messageId = message.id,
                content = content,
                onClick = { onShowImage(message) },
            )
          }

          is MessageContentUiModel.Video -> {
            VideoMessageBubble(
                messageId = message.id,
                content = content,
                onClick = { onShowVideo(message) },
            )
          }

          is MessageContentUiModel.Voice -> {
            PrepareMediaBubble(
                mediaPlaybackStateAndAction = mediaPlaybackStateAndAction,
                messageId = message.id,
                content = content,
            ) { currentPlaybackState, inactivePlayPosition ->
              VoiceMessageBubble(
                  messageId = message.id,
                  sender = message.sender,
                  content = content,
                  isOwnMessage = isOwn,
                  playbackState = currentPlaybackState,
                  activePlayPositionHolder = mediaPlaybackStateAndAction.activePlayPositionHolder,
                  inactivePlayPosition = inactivePlayPosition,
                  onTogglePlay = { mediaPlaybackStateAndAction.onTogglePlay(message) },
                  onSeek = { ratio -> mediaPlaybackStateAndAction.onSeek(message, ratio) },
                  onSpeedChange = { mediaPlaybackStateAndAction.onCyclePlaybackSpeed(message.id) },
                  modifier = modifier,
              )
            }
          }
          is MessageContentUiModel.Audio -> {
            PrepareMediaBubble(
                mediaPlaybackStateAndAction = mediaPlaybackStateAndAction,
                messageId = message.id,
                content = content,
            ) { currentPlaybackState, inactivePlayPosition ->
              AudioMessageBubble(
                  messageId = message.id,
                  content = content,
                  isOwnMessage = isOwn,
                  playbackState = currentPlaybackState,
                  activePlayPositionHolder = mediaPlaybackStateAndAction.activePlayPositionHolder,
                  inactivePlayPosition = inactivePlayPosition,
                  onTogglePlay = { mediaPlaybackStateAndAction.onTogglePlay(message) },
                  onSeek = { ratio -> mediaPlaybackStateAndAction.onSeek(message, ratio) },
                  onSpeedChange = { mediaPlaybackStateAndAction.onCyclePlaybackSpeed(message.id) },
                  modifier = modifier,
              )
            }
          }

          is MessageContentUiModel.GenericFile -> {
            GenericFileMessageBubble(
                content = content,
                onClick = { onOpenFile(message) },
                modifier = modifier,
            )
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

/**
 * Read value from activePlaybackStateHolder, get inactive-play-position, and then rendering the
 * bubble
 */
@Composable
private fun PrepareMediaBubble(
    mediaPlaybackStateAndAction: MediaPlaybackStateAndAction,
    messageId: MessageId,
    content: MessageContentUiModel.AudioPlaySupported,
    bubble:
        @Composable
        (
            currentPlaybackState: MediaPlaybackState,
            inactivePlayPosition: Duration,
        ) -> Unit,
) {
  // When target change, those will be re-composition.

  val audioPlaybackState = mediaPlaybackStateAndAction.activePlaybackStateHolder.value
  val isActive = audioPlaybackState.isThisMediaActive(toMediaInputId(messageId))
  val currentPlaybackState =
      if (isActive) {
        audioPlaybackState
      } else {
        MediaPlaybackState(duration = content.duration)
      }
  // it will be recorded before switching to next one
  val inactivePlayPosition = mediaPlaybackStateAndAction.inactivePlayPositionGetter(messageId)
  // Render bubble
  bubble(currentPlaybackState, inactivePlayPosition)
}
