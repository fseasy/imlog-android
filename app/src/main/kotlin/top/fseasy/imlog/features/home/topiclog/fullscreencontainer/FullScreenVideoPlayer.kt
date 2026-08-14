package top.fseasy.imlog.features.home.topiclog.fullscreencontainer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.features.home.topiclog.timeline.AudioPlaybackState
import top.fseasy.imlog.features.home.topiclog.timeline.MessageContentUiModel
import kotlin.time.Duration

@Composable
fun FullScreenVideoPlayer(
  messageId: MessageId,
  player: ExoPlayer,
  content: MessageContentUiModel.Video,
  playbackState: AudioPlaybackState,
  activePlayPositionHolder: State<Duration>,
  onTogglePlay: () -> Unit,
  onSeek: (Float) -> Unit,
  onSpeedChange: () -> Unit,
  modifier: Modifier = Modifier,
) {

  // Local UI state
  var areControlsVisible by remember { mutableStateOf(true) }
  var isScrubbing by remember { mutableStateOf(false) }
  var scrubRatio by remember { mutableFloatStateOf(0f) }

  LaunchedEffect(messageId) {
    onTogglePlay()
  }

  val isPlaying = playbackState.isThisMessagePlaying(messageId)
  // hidden controls after 3 seconds
  LaunchedEffect(areControlsVisible, isPlaying, isScrubbing) {
    if (areControlsVisible && isPlaying && !isScrubbing) {
      delay(3.seconds)
      areControlsVisible = false
    }
  }

  Box(
      modifier =
          modifier.fillMaxSize().background(Color.Black).clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = null, // disable indication
          ) {
            areControlsVisible = !areControlsVisible
          }
  ) {

      PlayerSurface(
          player = player,
          modifier = Modifier.fillMaxSize(),
      )

    if (playbackState.status == PlaybackStatus.Buffering) {
      CircularProgressIndicator(
          color = Color.White.copy(alpha = 0.8f),
          modifier = Modifier.align(Alignment.Center),
      )
    }

    AnimatedVisibility(
        visible = areControlsVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.fillMaxSize(),
    ) {
      Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))) {
        // close button
        IconButton(
            onClick = onCloseClick,
            modifier = Modifier.align(Alignment.TopStart).padding(top = 40.dp, start = 16.dp),
        ) {
          Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "关闭",
              tint = Color.White,
          )
        }

        // 【Center】微信巨型中央播放/暂停图标
        IconButton(
            onClick = { mediaPlayerStateHolder.togglePlayPause(mediaInput) },
            modifier = Modifier.size(72.dp).align(Alignment.Center),
        ) {
          Icon(
              imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
              contentDescription = if (isPlaying) "暂停" else "播放",
              tint = Color.White.copy(alpha = 0.9f),
              modifier = Modifier.fillMaxSize(),
          )
        }

        // 【Bottom】底部微信进度条与时间显示
        Row(
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          // 时间显示 (例如 00:08 / 01:30)
          Text(
              text = "${formatTime(currentPositionMs)} / ${formatTime(totalDurationMs)}",
              color = Color.White,
              fontSize = 12.sp,
              modifier = Modifier.padding(end = 12.dp),
          )

          // 进度条 Slider
          val sliderValue = (currentPositionMs.toFloat() / totalDurationMs).coerceIn(0f, 1f)
          Slider(
              value = sliderValue,
              onValueChange = { ratio ->
                isScrubbing = true
                scrubRatio = ratio
              },
              onValueChangeFinished = {
                mediaPlayerStateHolder.seekToRatio(mediaInput, scrubRatio)
                isScrubbing = false
              },
              colors =
                  SliderDefaults.colors(
                      thumbColor = Color.White,
                      activeTrackColor = Color.White,
                      inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                  ),
              modifier = Modifier.weight(1f),
          )
        }
      }
    }
  }
}
