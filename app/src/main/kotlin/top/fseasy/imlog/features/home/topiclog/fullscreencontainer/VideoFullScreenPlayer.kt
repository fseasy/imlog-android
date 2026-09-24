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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
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
import top.fseasy.imlog.features.home.topiclog.timeline.aspectRatio
import top.fseasy.imlog.features.home.topiclog.timeline.messagebubble.WaveformSlider
import top.fseasy.imlog.features.home.topiclog.toMediaInputId
import top.fseasy.imlog.ui.theme.ImlogTheme
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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

  VideoFullScreenPlayerContent(
      content = content,
      messageId = messageId,
      playbackState = playbackState,
      activePlayPositionHolder = activePlayPositionHolder,
      inactivePlayPosition = inactivePlayPosition,
      onTogglePlay = onTogglePlay,
      onSeek = onSeek,
      onSpeedCycle = onSpeedCycle,
      onExit = onExit,
      videoSurface = {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
          PlayerSurface(
              player = player,
              modifier = Modifier.fillMaxWidth().aspectRatio(content.aspectRatio),
          )
        }
      },
      modifier = modifier,
  )
}

@Composable
fun VideoFullScreenPlayerContent(
    messageId: MessageId,
    content: MessageContentUiModel.Video,
    playbackState: MediaPlaybackState,
    activePlayPositionHolder: State<Duration>,
    inactivePlayPosition: Duration,
    onTogglePlay: () -> Unit,
    onSeek: (Float) -> Unit,
    onSpeedCycle: () -> Unit,
    onExit: () -> Unit,
    videoSurface: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
  var areControlsVisible by remember { mutableStateOf(true) }

  Box(
      modifier =
          modifier.fillMaxSize().background(Color.Black).clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = null,
          ) {
            areControlsVisible = !areControlsVisible
          }
  ) {
    videoSurface()

    AnimatedVisibility(
        visible = areControlsVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.fillMaxSize(),
    ) {
      Box(modifier = Modifier.fillMaxSize()) {
        // close button
        IconButton(
            onClick = onExit,
            modifier =
                Modifier.align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(16.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = CircleShape,
                    ),
        ) {
          Icon(
              imageVector = Icons.Rounded.Close,
              contentDescription = stringResource(R.string.term_close),
              tint = Color.White,
          )
        }

        val isVideoActive = playbackState.isThisMediaActive(toMediaInputId(messageId))
        PlayControllerDock(
            tintColor = Color.White,
            modifier =
                Modifier.fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 20.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(24.dp),
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            isPlaying = playbackState.isThisMediaPlaying(toMediaInputId(messageId)),
            duration = playbackState.duration,
            speed = playbackState.speed,
            amplitudes = content.amplitudes,
            onTogglePlay = onTogglePlay,
            onSeek = onSeek,
            onSpeedCycle = onSpeedCycle,
            positionProvider = {
              if (isVideoActive) {
                activePlayPositionHolder.value
              } else {
                inactivePlayPosition
              }
            },
        )
      }
    }
  }
}

@Composable
fun PlayControllerDock(
    isPlaying: Boolean,
    duration: Duration,
    speed: Float,
    amplitudes: List<Float>,
    onTogglePlay: () -> Unit,
    onSeek: (Float) -> Unit,
    onSpeedCycle: () -> Unit,
    positionProvider: () -> Duration,
    tintColor: Color,
    modifier: Modifier = Modifier,
) {
  Row(
      modifier = modifier.height(48.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
  ) {
    val playPauseDescription =
        stringResource(if (isPlaying) R.string.term_media_pause else R.string.term_media_play)
    IconButton(
        onClick = onTogglePlay,
        modifier = Modifier.fillMaxHeight(),
    ) {
      Icon(
          imageVector =
              if (isPlaying) {
                ImageVector.vectorResource(R.drawable.icon_pause)
              } else {
                Icons.Default.PlayArrow
              },
          contentDescription = playPauseDescription,
          tint = tintColor,
      )
    }

    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
      WaveformSlider(
          progressProvider = {
            val current = positionProvider()
            current.safeDivision(duration).coerceIn(0f, 1f)
          },
          amplitudes = amplitudes,
          tintColor = tintColor,
          onSeek = onSeek,
          stretchToFit = true,
          modifier = Modifier.fillMaxWidth().height(32.dp),
      )

      TimeAndSpeedLine(
          duration = duration,
          speed = speed,
          positionProvider = positionProvider,
          onSpeedCycle = onSpeedCycle,
          tintColor = tintColor,
      )
    }
  }
}

@Composable
private fun TimeAndSpeedLine(
    duration: Duration,
    speed: Float,
    positionProvider: () -> Duration,
    onSpeedCycle: () -> Unit,
    tintColor: Color,
) {
  Row(
      modifier = Modifier.fillMaxWidth().height(16.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Row {
      Text(
          text = positionProvider().toAppMessageTimeFormat(),
          style = MaterialTheme.typography.labelSmall,
          color = tintColor.copy(alpha = 0.7f),
      )
      Text(
          text = " / ${duration.toAppMessageTimeFormat()}",
          style = MaterialTheme.typography.labelSmall,
          color = tintColor.copy(alpha = 0.7f),
      )
    }

    Text(
        text = "${speed}x",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = tintColor,
        modifier =
            Modifier.clip(RoundedCornerShape(4.dp))
                .clickable(onClick = onSpeedCycle)
                .padding(horizontal = 4.dp, vertical = 2.dp),
    )
  }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun VideoFullScreenPlayerPreview() {
  ImlogTheme {
    VideoFullScreenPlayerContent(
        messageId = MessageId("msg_123"),
        content =
            MessageContentUiModel.Video(
                amplitudes = listOf(0.2f, 0.5f, 0.8f, 0.4f, 0.9f, 0.3f, 0.6f, 0.7f),
                storedFilename = "Test.mp4",
                sourceTemporaryUri = null,
                thumbnailPath = null,
                width = 1080,
                height = 720,
                duration = 30.seconds,
            ),
        playbackState =
            MediaPlaybackState(
                status = PlayerStatus.Playing,
                speed = 1.0f,
            ),
        activePlayPositionHolder = remember { mutableStateOf(20.seconds) },
        inactivePlayPosition = Duration.ZERO,
        onTogglePlay = {},
        onSeek = {},
        onSpeedCycle = {},
        onExit = {},
        videoSurface = {
          // placeholder
          Box(
              modifier = Modifier.fillMaxSize().background(Color(0xFF1E1E1E)),
              contentAlignment = Alignment.Center,
          ) {
            Text("Video Surface Preview", color = Color.DarkGray)
          }
        },
    )
  }
}
