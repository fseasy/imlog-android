package top.fseasy.imlog.features.home.topiclog.fullscreencontainer

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.media3.exoplayer.ExoPlayer
import top.fseasy.imlog.data.mapper.toActualFileOrUri
import top.fseasy.imlog.features.home.topiclog.MediaPlaybackStateAndAction
import top.fseasy.imlog.features.home.topiclog.ReadMediaPlaybackStateAndRender
import top.fseasy.imlog.features.home.topiclog.toMemoryCacheKey
import top.fseasy.imlog.features.home.topiclog.toOverlayTransitionElementId
import top.fseasy.imlog.ui.components.overlaylayout.OverlayLayoutScope

/** It's under OverlayLayoutScope, so it hold `dismiss` call */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun OverlayLayoutScope.FullScreenContainer(
    model: FullScreenContainerUiModel,
    player: ExoPlayer,
    mediaPlaybackStateAndAction: MediaPlaybackStateAndAction,
    modifier: Modifier = Modifier,
) {

  when (model) {
    is FullScreenContainerUiModel.ImageShow -> {
      ImageFullScreenViewer(
          overlayTransitionElementId = toOverlayTransitionElementId(model.id),
          imageUrl = model.path.toActualFileOrUri(),
          thumbnailCacheKey = toMemoryCacheKey(model.id),
          modifier = modifier,
      )
    }

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
                  onExit = {},
                  modifier = modifier,
              )
            },
        )
    is FullScreenContainerUiModel.TextSelection ->
        MessageTextSelectionFullScreen(
            text = model.message.content.text,
            onDismissRequest = {},
            modifier = modifier,
        )
  }
}
