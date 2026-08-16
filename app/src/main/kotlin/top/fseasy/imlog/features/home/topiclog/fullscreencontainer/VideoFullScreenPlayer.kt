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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import top.fseasy.imlog.data.util.MediaPlaybackState
import top.fseasy.imlog.data.util.PlayerStatus
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.domain.util.safeDivision
import top.fseasy.imlog.domain.util.toAppMessageTimeFormat
import top.fseasy.imlog.features.home.topiclog.timeline.MessageContentUiModel
import top.fseasy.imlog.features.home.topiclog.timeline.messagebubble.WaveformSlider
import top.fseasy.imlog.features.home.topiclog.toMediaInputId
import top.fseasy.imlog.ui.components.AppCircularProgress
import top.fseasy.imlog.ui.components.AppTextButton
import kotlin.time.Duration

@Composable
fun VideoFullScreenPlayer(
    messageId: MessageId,
    content: MessageContentUiModel.Video,
    player: ExoPlayer,
    playbackState: MediaPlaybackState,
    activePlayPositionHolder: State<Duration>,
    inactivePlayPosition: Duration,
    onTogglePlay: () -> Unit,
    onSeek: (Float) -> Unit,
    onSpeedCycle: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {

  var areControlsVisible by remember { mutableStateOf(true) }

  LaunchedEffect(messageId) {
    onTogglePlay()
  }

  val isPlaying = playbackState.isThisMediaPlaying(toMediaInputId(messageId))
  val isActive = playbackState.isThisMediaActive(toMediaInputId(messageId))

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

        PlayControllerRow(
            amplitudes = content.amplitudes,
            isActive = isActive,
            isPlaying = isPlaying,
            duration = playbackState.duration,
            speed = playbackState.speed,
            activePlayPositionHolder = activePlayPositionHolder,
            inactivePlayPosition = inactivePlayPosition,
            onTogglePlay = onTogglePlay,
            onSeek = onSeek,
            onSpeedCycle = onSpeedCycle,
            tintColor = tintColor,
            modifier = Modifier.fillMaxSize().align(Alignment.BottomEnd).padding(10.dp, 6.dp),
        )
      }
    }
  }
}

/** Used for Audio/Voice bubble */
@Composable
fun PlayControllerRow(
    amplitudes: List<Float>,
    isActive: Boolean,
    isPlaying: Boolean,
    duration: Duration,
    speed: Float,
    activePlayPositionHolder: State<Duration>,
    inactivePlayPosition: Duration,
    onTogglePlay: () -> Unit,
    onSeek: (Float) -> Unit,
    onSpeedCycle: () -> Unit,
    tintColor: Color,
    modifier: Modifier = Modifier,
) {
  val playPosition = if (isActive) activePlayPositionHolder.value else inactivePlayPosition

  Row(
      modifier = modifier.height(48.dp), // waveform 32 + time/speed 16
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
  ) {
    // play/pause
    val playPauseDescription =
        stringResource(if (isPlaying) R.string.term_media_pause else R.string.term_media_play)
    IconButton(onClick = onTogglePlay, modifier.fillMaxHeight()) {
      Icon(
          imageVector =
              if (isPlaying) ImageVector.vectorResource(R.drawable.icon_pause)
              else Icons.Default.PlayArrow,
          contentDescription = playPauseDescription,
          tint = tintColor,
      )
    }
    // === waveform slider
    // === TIME ---- Speed
    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
      val progress = playPosition.safeDivision(duration).coerceIn(0f, 1f)
      // === waveform slider
      Row(modifier = Modifier.fillMaxWidth()) {
        WaveformSlider(
            progress = progress,
            amplitudes = amplitudes,
            tintColor = tintColor,
            onSeek = onSeek,
            modifier = Modifier.fillMaxWidth().height(32.dp), // waveform height
        )
      }
      // === TIME ---- Speed
      Row(
          modifier = Modifier.fillMaxWidth().height(16.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        // TIME
        Row() {
          Text(
              text = playPosition.toAppMessageTimeFormat(),
              style = MaterialTheme.typography.labelSmall,
              color = tintColor.copy(alpha = 0.6f),
          )
          Text(
              text = "/",
              style = MaterialTheme.typography.labelSmall,
              color = tintColor.copy(0.6f),
          )

          Text(
              text = duration.toAppMessageTimeFormat(),
              style = MaterialTheme.typography.labelSmall,
              color = tintColor.copy(alpha = 0.6f),
          )
        }
      }
      AppTextButton(
          onClick = onSpeedCycle,
          text = "${speed}x",
          modifier = Modifier.background(MaterialTheme.colorScheme.surface),
          enabled = true,
      )
    }
  }
}
