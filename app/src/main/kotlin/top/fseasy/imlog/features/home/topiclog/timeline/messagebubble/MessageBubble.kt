package top.fseasy.imlog.features.home.topiclog.timeline.messagebubble

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import top.fseasy.imlog.features.home.topiclog.MediaPlaybackStateAndAction
import top.fseasy.imlog.features.home.topiclog.ReadMediaPlaybackStateAndRender
import top.fseasy.imlog.features.home.topiclog.ShowFullScreenMessageUiModelAction
import top.fseasy.imlog.features.home.topiclog.timeline.AnyMessageUiModel
import top.fseasy.imlog.features.home.topiclog.timeline.MessageContentUiModel
import top.fseasy.imlog.features.home.topiclog.timeline.MessageSenderUiModel
import top.fseasy.imlog.features.home.topiclog.timeline.MessageUiModel
import top.fseasy.imlog.features.home.topiclog.timeline.narrow
import top.fseasy.imlog.ui.components.contextmenu.contextMenuClickable

@Composable
fun MessageBubble(
    message: AnyMessageUiModel,
    mediaPlaybackStateAndAction: MediaPlaybackStateAndAction,
    onShowFullScreenMessage: ShowFullScreenMessageUiModelAction,
    onOpenFile: (MessageUiModel<MessageContentUiModel.GenericFile>) -> Unit,
    onShowContextMenu: (IntOffset) -> Unit,
    modifier: Modifier = Modifier,
) {
  val isOwn = message.sender is MessageSenderUiModel.Own

  Row(
      modifier = modifier.fillMaxWidth(),
      horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
  ) {
    when (val content = message.content) {
      is MessageContentUiModel.Text -> {
        BubbleCard(
            onShowContextMenu = onShowContextMenu,
            containerColor = getCardContainerColor(isOwn),
        ) {
          ColumnWithTimeText(message.formatedCreatedAt) {
            TextMessageBubble(
                text = content.text,
            )
          }
        }
      }

      is MessageContentUiModel.Image -> {
        BubbleCard(
            onClick = { onShowFullScreenMessage.showImage(message.narrow()) },
            onShowContextMenu = onShowContextMenu,
            containerColor = Color.Transparent,
        ) {
          BoxWithFloatingTimeBadge(message.formatedCreatedAt) {
            ImageMessageBubble(
                messageId = message.id,
                content = content,
            )
          }
        }
      }

      is MessageContentUiModel.Video -> {
        BubbleCard(
            onClick = { onShowFullScreenMessage.showVideo(message.narrow()) },
            onShowContextMenu = onShowContextMenu,
            containerColor = Color.Transparent,
        ) {
          BoxWithFloatingTimeBadge(message.formatedCreatedAt) {
            VideoMessageBubble(
                messageId = message.id,
                content = content,
            )
          }
        }
      }

      is MessageContentUiModel.Voice -> {
        BubbleCard(
            onShowContextMenu = onShowContextMenu,
            containerColor = getCardContainerColor(isOwn),
        ) {
          ColumnWithTimeText(message.formatedCreatedAt) {
            ReadMediaPlaybackStateAndRender(
                mediaPlaybackStateAndAction = mediaPlaybackStateAndAction,
                messageId = message.id,
                messageContent = content,
            ) { currentPlaybackState, inactivePlayPosition ->
              VoiceMessageBubble(
                  messageId = message.id,
                  sender = message.sender,
                  content = content,
                  isOwnMessage = isOwn,
                  playbackState = currentPlaybackState,
                  activePlayPositionHolder = mediaPlaybackStateAndAction.activePlayPositionHolder,
                  inactivePlayPosition = inactivePlayPosition,
                  onTogglePlay = { mediaPlaybackStateAndAction.onTogglePlay(message.narrow()) },
                  onSeek = { ratio -> mediaPlaybackStateAndAction.onSeek(message.narrow(), ratio) },
                  onSpeedChange = { mediaPlaybackStateAndAction.onCyclePlaybackSpeed(message.id) },
              )
            }
          }
        }
      }

      is MessageContentUiModel.Audio -> {
        BubbleCard(
            onShowContextMenu = onShowContextMenu,
            containerColor = getCardContainerColor(isOwn),
        ) {
          ColumnWithTimeText(message.formatedCreatedAt) {
            ReadMediaPlaybackStateAndRender(
                mediaPlaybackStateAndAction = mediaPlaybackStateAndAction,
                messageId = message.id,
                messageContent = content,
            ) { currentPlaybackState, inactivePlayPosition ->
              AudioMessageBubble(
                  messageId = message.id,
                  content = content,
                  isOwnMessage = isOwn,
                  playbackState = currentPlaybackState,
                  activePlayPositionHolder = mediaPlaybackStateAndAction.activePlayPositionHolder,
                  inactivePlayPosition = inactivePlayPosition,
                  onTogglePlay = { mediaPlaybackStateAndAction.onTogglePlay(message.narrow()) },
                  onSeek = { ratio -> mediaPlaybackStateAndAction.onSeek(message.narrow(), ratio) },
                  onSpeedChange = { mediaPlaybackStateAndAction.onCyclePlaybackSpeed(message.id) },
              )
            }
          }
        }
      }

      is MessageContentUiModel.GenericFile -> {
        BubbleCard(
            onClick = { onOpenFile(message.narrow()) },
            onShowContextMenu = onShowContextMenu,
            containerColor = getCardContainerColor(isOwn),
        ) {
          ColumnWithTimeText(message.formatedCreatedAt) {
            GenericFileMessageBubble(content = content)
          }
        }
      }
    }
  }
}

/** set the bubble card with binding the [contextMenuClickable] */
@Composable
private fun BubbleCard(
    containerColor: Color,
    onShowContextMenu: (IntOffset) -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
  Surface(
      shape = RoundedCornerShape(16.dp),
      color = containerColor,
      modifier =
          modifier
              .widthIn(min = BUBBLE_MIN_WIDTH_IN_DP.dp, max = BUBBLE_MAX_WIDTH_IN_DP.dp)
              .contextMenuClickable(
                  onClick = { onClick?.invoke() },
                  onLongClickWithPosition = onShowContextMenu,
              ),
  ) {
    content()
  }
}

@Composable
private fun getCardContainerColor(isOwn: Boolean) =
    if (isOwn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant

@Composable
private fun BoxWithFloatingTimeBadge(
    timeText: String,
    bubbleContent: @Composable () -> Unit,
) =
    Box() {
      bubbleContent()
      MessageTimeOverlayBadge(
          timeText = timeText,
          modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
      )
    }

@Composable
private fun ColumnWithTimeText(timeText: String, bubbleContent: @Composable () -> Unit) =
    Column(modifier = Modifier.padding(4.dp)) {
      bubbleContent()
      MessageTimeText(
          timeText = timeText,
          modifier = Modifier.align(Alignment.End),
      )
    }
