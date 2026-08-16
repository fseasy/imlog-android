package top.fseasy.imlog.features.home.topiclog.timeline.messagebubble

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
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
  val isActive = playbackState.isThisMediaActive(toMediaInputId(messageId))
  val isPlaying = playbackState.isThisMediaPlaying(toMediaInputId(messageId))

  val tintColor =
      if (isOwnMessage) MaterialTheme.colorScheme.onPrimary
      else MaterialTheme.colorScheme.onSurfaceVariant

  Column(modifier = Modifier.padding(8.dp)) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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

      val playPauseDescription =
          stringResource(if (isPlaying) R.string.term_media_pause else R.string.term_media_play)
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
          isActive = isActive,
          duration = playbackState.duration,
          activePlayPositionHolder = activePlayPositionHolder,
          inactivePlayPosition = inactivePlayPosition,
          onSeek = onSeek,
          tintColor = tintColor,
      )
    }
  }
}

@Composable
private fun LeftMostArea(
    isActive: Boolean,
    mimeType: String,
    onChangeSpeed: () -> Unit,
    modifier: Modifier = Modifier,
    playbackSpeed: Float,
    tintColor: Color,
) {

  Box(
      modifier =
          Modifier.fillMaxSize()
              .background(
                  color = MaterialTheme.colorScheme.surfaceVariant,
                  shape = CircleShape,
              ),
  ) {
    if (isActive) {
      TextButton(
          onClick = onChangeSpeed,
          modifier = modifier,
      ) {
        Text(
            text = "${playbackSpeed}x",
            style = MaterialTheme.typography.labelSmall,
            color = tintColor,
        )
      }
    } else {
      Icon(
          painterResource(mimeTypeToIconResId(mimeType)),
          contentDescription = stringResource(R.string.term_audio_message),
          tint = MaterialTheme.colorScheme.primary,
      )
    }
  }
}
