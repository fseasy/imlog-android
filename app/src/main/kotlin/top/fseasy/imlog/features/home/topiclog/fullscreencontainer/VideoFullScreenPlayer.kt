package top.fseasy.imlog.features.home.topiclog.fullscreencontainer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import top.fseasy.imlog.R
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.data.util.MediaPlaybackState
import top.fseasy.imlog.features.home.topiclog.timeline.MessageContentUiModel
import top.fseasy.imlog.data.util.PlayerStatus
import top.fseasy.imlog.features.home.topiclog.timeline.messagebubble.WaveformWithProgressColumn
import top.fseasy.imlog.ui.components.AppCircularProgress
import kotlin.time.Duration

@Composable
fun VideoFullScreenPlayer(
  messageId: MessageId,
  player: ExoPlayer,
  content: MessageContentUiModel.Video,
  playbackState: MediaPlaybackState,
  activePlayPositionHolder: State<Duration>,
  onTogglePlay: () -> Unit,
  onSeek: (Float) -> Unit,
  onExit: () -> Unit,
  onSpeedChange: () -> Unit,
  modifier: Modifier = Modifier,
) {

  var areControlsVisible by remember { mutableStateOf(true) }

  LaunchedEffect(messageId) {
    onTogglePlay()
  }

  val isPlaying = playbackState.isThisMediaPlaying(messageId)

  Box(
      modifier =
          modifier.fillMaxSize().background(Color.Black).clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = null, // disable indication
          ) {
            // Switch visibility when click on the empty area
            areControlsVisible = !areControlsVisible
          }
  ) {
    PlayerSurface(
        player = player,
        modifier = Modifier.fillMaxSize(),
    )

    if (playbackState.status == PlayerStatus.Buffering) {
      AppCircularProgress(
          modifier = Modifier.align(Alignment.Center),
      )
    }

    AnimatedVisibility(
        visible = areControlsVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.fillMaxSize(),
    ) {
      Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        val tintColor = MaterialTheme.colorScheme.onSurface
        // close button
        IconButton(
            onClick = onExit,
            modifier = Modifier.align(Alignment.TopStart).padding(top = 40.dp, start = 16.dp),
        ) {
          Icon(
              imageVector = Icons.Default.Close,
              contentDescription = stringResource(R.string.btn_close),
              tint = tintColor,
          )
        }

        // Bottom: play control & Extra function for Future
        Column(
            modifier = Modifier.fillMaxSize().align(Alignment.BottomEnd),
            verticalArrangement = Arrangement.Bottom,
        ) {
          Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceBetween) {
            val playPauseDescription =
                stringResource(
                    if (isPlaying) R.string.term_media_pause else R.string.term_media_play
                )

            IconButton(onClick = onTogglePlay) {
              Icon(
                  imageVector =
                      if (isPlaying) ImageVector.vectorResource(R.drawable.icon_pause)
                      else Icons.Default.PlayArrow,
                  contentDescription = playPauseDescription,
                  tint = tintColor,
              )
            }

            WaveformWithProgressColumn(
                amplitudes = content.amplitudes,
                isActive = areControlsVisible,
                duration = playbackState.duration,
                activePlayPositionHolder = activePlayPositionHolder,
                inactivePlayPosition = inactivePlayPosition,
                onSeek = onSeek,
                tintColor = tintColor,
                modifier = Modifier.weight(1f).padding(4.dp),
            )
          }
        }
      }
    }
  }
}
