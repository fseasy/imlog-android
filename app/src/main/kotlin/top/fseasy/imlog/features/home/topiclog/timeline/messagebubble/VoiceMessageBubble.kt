package top.fseasy.imlog.features.home.topiclog.timeline.messagebubble

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.fseasy.imlog.features.home.topiclog.timeline.MessageContentUiModel

@Composable
fun VoiceMessageBubble(
    messageId: String,
    content: MessageContentUiModel.Voice,
    isOwnMessage: Boolean,
    isPlaying: Boolean,
    currentPositionMs: Long,
    playbackSpeed: Float,
    onPlayPauseClick: () -> Unit,
    onSeek: (Float) -> Unit,
    onSpeedChange: () -> Unit,
) {
  val tintColor =
      if (isOwnMessage) MaterialTheme.colorScheme.onPrimary
      else MaterialTheme.colorScheme.onSurfaceVariant

  Column(modifier = Modifier.padding(8.dp)) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      // 播放/暂停 按钮
      IconButton(onClick = onPlayPauseClick) {
        Icon(
            imageVector =
                if (isPlaying) Icons.Default.PlayArrow
                else Icons.Default.PlayArrow, // 可替换为 Pause 图标
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = tintColor,
        )
      }

      Column(modifier = Modifier.weight(1f)) {
        // 进度条 (也可以用 Canvas 自定义波形图)
        val progress =
            if (content.durationMs > 0) {
              (currentPositionMs.toFloat() / content.durationMs.toFloat()).coerceIn(0f, 1f)
            } else 0f

        Slider(
            value = progress,
            onValueChange = { onSeek(it) },
            modifier = Modifier.fillMaxWidth(),
            colors =
                SliderDefaults.colors(
                    thumbColor = tintColor,
                    activeTrackColor = tintColor,
                ),
        )

        // 时间展示
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
              text = formatMsToMmSs(if (isPlaying) currentPositionMs else content.durationMs),
              style = MaterialTheme.typography.labelSmall,
              color = tintColor.copy(alpha = 0.8f),
          )
          if (isPlaying) {
            Text(
                text = formatMsToMmSs(content.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = tintColor.copy(alpha = 0.6f),
            )
          }
        }
      }
    }

    // 播放倍速切换
    TextButton(
        onClick = onSpeedChange,
        modifier = Modifier.align(Alignment.End),
    ) {
      Text(
          text = "${playbackSpeed}x",
          style = MaterialTheme.typography.labelSmall,
          color = tintColor,
      )
    }
  }
}
