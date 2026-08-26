package top.fseasy.imlog.features.home.topiclog.timeline.messagebubble

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.fseasy.imlog.R
import top.fseasy.imlog.data.util.MediaPlaybackState
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.features.home.topiclog.timeline.MessageContentUiModel
import top.fseasy.imlog.features.home.topiclog.toMediaInputId
import top.fseasy.imlog.ui.util.mimeTypeToIconResId
import kotlin.time.Duration

@Composable
fun AudioMessageBubble(
    messageId: MessageId,
    content: MessageContentUiModel.Audio,
    isOwnMessage: Boolean,
    playbackState: MediaPlaybackState,
    activePlayPositionHolder: State<Duration>,
    inactivePlayPosition: Duration,
    onTogglePlay: () -> Unit,
    onSeek: (Float) -> Unit,
    onSpeedChange: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val inputId = toMediaInputId(messageId)
  val isActive = playbackState.isThisMediaActive(inputId)
  val isPlaying = playbackState.isThisMediaPlaying(inputId)

  // Duration fallback: Prefer content's duration if media playback state duration is 0
  val totalDuration =
      if (isActive && playbackState.duration > Duration.ZERO) {
        playbackState.duration
      } else {
        content.duration // or content's pre-calculated duration
      }
  val tintColor =
      if (isOwnMessage) {
        MaterialTheme.colorScheme.onPrimary
      } else {
        MaterialTheme.colorScheme.onSurfaceVariant
      }

  Row(
      modifier = modifier.padding(horizontal = 8.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    LeftMostArea(
        isActive = isActive,
        mimeType = content.mimeType,
        onChangeSpeed = onSpeedChange,
        modifier = modifier.size(36.dp),
        playbackSpeed = playbackState.speed,
        tintColor = tintColor,
    )

    // 2. Play / Pause Button
    val playPauseDescription =
        stringResource(if (isPlaying) R.string.term_media_pause else R.string.term_media_play)
    IconButton(
        onClick = onTogglePlay,
        modifier = Modifier.size(40.dp),
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
          modifier = Modifier.size(28.dp),
      )
    }

    Spacer(modifier = Modifier.width(4.dp))

    // 3. Waveform + Time info (Takes remaining space)
    WaveformWithProgressColumn(
        amplitudes = content.amplitudes,
        isActive = isActive,
        duration = totalDuration,
        activePlayPositionHolder = activePlayPositionHolder,
        inactivePlayPosition = inactivePlayPosition,
        onSeek = onSeek,
        tintColor = tintColor,
        modifier = Modifier.weight(1f), // Ensures full available width without clipping
    )
  }
}

@Composable
private fun LeftMostArea(
    isActive: Boolean,
    mimeType: String,
    onChangeSpeed: () -> Unit,
    playbackSpeed: Float,
    tintColor: Color,
    modifier: Modifier = Modifier,
) {

  Box(
      modifier = modifier,
      contentAlignment = Alignment.Center,
  ) {
    if (isActive) {
      val speedText =
          if (playbackSpeed % 1.0f == 0f) {
            "${playbackSpeed.toInt()}X"
          } else {
            "${playbackSpeed}X"
          }

      Box(
          modifier =
              Modifier.fillMaxSize()
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.surfaceVariant)
                  .clickable(
                      role = Role.Button,
                      onClick = onChangeSpeed,
                  ),
          contentAlignment = Alignment.Center,
      ) {
        Text(
            text = speedText,
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                ),
            color = tintColor,
        )
      }
    } else {
      Box(
          modifier =
              Modifier.fillMaxSize()
                  .align(Alignment.Center)
                  .background(MaterialTheme.colorScheme.surface, CircleShape)
                  .padding(2.dp),
          contentAlignment = Alignment.Center,
      ) {
        Icon(
            painterResource(mimeTypeToIconResId(mimeType)),
            contentDescription = stringResource(R.string.term_audio_message),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxSize(),
        )
      }
    }
  }
}
