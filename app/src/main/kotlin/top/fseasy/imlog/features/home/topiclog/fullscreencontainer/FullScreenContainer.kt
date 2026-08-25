package top.fseasy.imlog.features.home.topiclog.fullscreencontainer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.media3.exoplayer.ExoPlayer
import top.fseasy.imlog.data.mapper.toActualFileOrUri
import top.fseasy.imlog.features.home.topiclog.MediaPlaybackStateAndAction
import top.fseasy.imlog.features.home.topiclog.ReadMediaPlaybackStateAndRender
import top.fseasy.imlog.features.home.topiclog.timeline.FullScreenMessageUiModel
import top.fseasy.imlog.features.home.topiclog.timeline.MessageContentUiModel

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun FullScreenContainer(
    model: FullScreenMessageUiModel,
    player: ExoPlayer,
    mediaPlaybackStateAndAction: MediaPlaybackStateAndAction,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {

  BackHandler() { onClose() }

  Box(
      modifier =
          modifier
              .fillMaxSize()
              .systemBarsPadding()
              .navigationBarsPadding()
              .background(MaterialTheme.colorScheme.surface)
  ) {
    when (val content = model.message.content) {
      is MessageContentUiModel.Image -> {
        ImageFullScreenViewer(
            imageUrl = model.path.toActualFileOrUri(),
            onDismiss = onClose,
            modifier = Modifier.fillMaxSize(),
        )
      }
      is MessageContentUiModel.Video ->
          ReadMediaPlaybackStateAndRender(
              mediaPlaybackStateAndAction = mediaPlaybackStateAndAction,
              messageId = model.message.id,
              messageContent = content,
              renderContent = { currentPlaybackState, inactivePlayPosition ->
                VideoFullScreenPlayer(
                    messageId = model.message.id,
                    content = content,
                    player = player,
                    playbackState = currentPlaybackState,
                    activePlayPositionHolder = mediaPlaybackStateAndAction.activePlayPositionHolder,
                    inactivePlayPosition = inactivePlayPosition,
                    onTogglePlay = { mediaPlaybackStateAndAction.onTogglePlay(model.message) },
                    onSeek = { ratio -> mediaPlaybackStateAndAction.onSeek(model.message, ratio) },
                    onSpeedCycle = {
                      mediaPlaybackStateAndAction.onCyclePlaybackSpeed(model.message.id)
                    },
                    onExit = onClose,
                    modifier = Modifier.fillMaxSize(),
                )
              },
          )
      else -> error("Unsupported FullScreen View for type: ${content::class.qualifiedName}")
    }
  }
}
