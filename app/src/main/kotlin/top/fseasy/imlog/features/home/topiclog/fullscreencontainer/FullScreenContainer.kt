package top.fseasy.imlog.features.home.topiclog.fullscreencontainer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.media3.exoplayer.ExoPlayer
import top.fseasy.imlog.data.mapper.toActualFileOrUri
import top.fseasy.imlog.features.home.topiclog.MediaPlaybackStateAndAction
import top.fseasy.imlog.features.home.topiclog.ReadMediaPlaybackStateAndRender

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun FullScreenContainer(
    model: FullScreenContainerUiModel,
    player: ExoPlayer,
    mediaPlaybackStateAndAction: MediaPlaybackStateAndAction,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {

  BackHandler() { onClose() }

  when (model) {
    is FullScreenContainerUiModel.ImageShow ->
        ImageFullScreenViewer(
            imageUrl = model.path.toActualFileOrUri(),
            onDismiss = onClose,
            modifier = modifier,
        )

    is FullScreenContainerUiModel.VideoShow ->
        ReadMediaPlaybackStateAndRender(
            mediaPlaybackStateAndAction = mediaPlaybackStateAndAction,
            messageId = model.message.id,
            messageContent = model.message.content,
            renderContent = { currentPlaybackState, inactivePlayPosition ->
              VideoFullScreenPlayer(
                  messageId = model.message.id,
                  content = model.message.content,
                  player = player,
                  playbackState = currentPlaybackState,
                  activePlayPositionHolder = mediaPlaybackStateAndAction.activePlayPositionHolder,
                  inactivePlayPosition = inactivePlayPosition,
                  onTogglePlay = { mediaPlaybackStateAndAction.onTogglePlay(model.message) },
                  onSeek = { ratio ->
                    mediaPlaybackStateAndAction.onSeek(model.message, ratio)
                  },
                  onSpeedCycle = {
                    mediaPlaybackStateAndAction.onCyclePlaybackSpeed(model.message.id)
                  },
                  onExit = onClose,
                  modifier = modifier,
              )
            },
        )
    is FullScreenContainerUiModel.TextSelection ->
        MessageTextSelectionFullScreen(
            messageId = model.message.id,
            text = model.message.content.text,
            onDismiss = onClose,
            modifier = modifier,
        )
  }
}
