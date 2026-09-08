package top.fseasy.imlog.features.home.topiclog

import androidx.compose.runtime.Composable
import top.fseasy.imlog.data.util.MediaPlaybackState
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.features.home.topiclog.timeline.MessageContentUiModel
import kotlin.time.Duration

/**
 * Read `.activePlaybackStateHolder.value` and `.inactivePlayPositionGetter` of
 * MediaPlaybackStateAndAction, then prepares a PlaybackState based on the current message isActive
 * state.
 *
 * When target change, those will be re-composition.
 */
@Composable
fun ReadMediaPlaybackStateAndRender(
  mediaPlaybackStateAndAction: MediaPlaybackStateAndAction,
  messageId: MessageId,
  messageContent: MessageContentUiModel.AudioPlaySupported,
  renderContent:
        @Composable
        (
          currentPlaybackState: MediaPlaybackState,
          inactivePlayPosition: Duration,
        ) -> Unit,
) {
  val audioPlaybackState = mediaPlaybackStateAndAction.activePlaybackStateHolder.value
  val isActive = audioPlaybackState.isThisMediaActive(toMediaInputId(messageId))
  val currentPlaybackState =
      if (isActive) {
        audioPlaybackState
      } else {
        MediaPlaybackState(duration = messageContent.duration)
      }
  // it will be recorded before switching to next one
  val inactivePlayPosition = mediaPlaybackStateAndAction.inactivePlayPositionGetter(messageId)
  // Render bubble
  renderContent(currentPlaybackState, inactivePlayPosition)
}