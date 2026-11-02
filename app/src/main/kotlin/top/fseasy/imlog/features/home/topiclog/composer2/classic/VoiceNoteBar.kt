package top.fseasy.imlog.features.home.topiclog.composer2.classic

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import top.fseasy.imlog.R
import top.fseasy.imlog.ui.components.PlayerDurationText
import top.fseasy.imlog.ui.theme.ImlogTheme

sealed interface VoiceNoteAction {
  data object Delete : VoiceNoteAction

  data object PauseRecording : VoiceNoteAction

  data object ResumeRecording : VoiceNoteAction

  data object TogglePlayPreview : VoiceNoteAction

  data class SeekPreview(val progress: Float) : VoiceNoteAction

  data object Send : VoiceNoteAction
}

@Composable
fun VoiceNoteBar(
    isPaused: Boolean,
    formattedDuration: String,
    isPlayingPreview: Boolean,
    previewProgress: Float,
    onAction: (VoiceNoteAction) -> Unit,
    modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  val durationTextStyle =
      MaterialTheme.typography.bodyLarge.copy(
          fontWeight = FontWeight.Bold,
          color = colorScheme.onSurface,
      )

  Column(
      modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    // ---------------- 顶部：时间 / 试听播放 / 波形进度 ----------------
    AnimatedContent(
        targetState = isPaused,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "VoiceNoteTopTrackTransition",
    ) { paused ->
      if (!paused) {
        // [Recording 录音中]
        Row(
            modifier = Modifier.fillMaxWidth().height(36.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          PlayerDurationText(
              text = formattedDuration,
              style = durationTextStyle,
          )

          // 录音指示条：底色使用 surfaceVariant，进度使用 primary 浅半透明
          LinearProgressIndicator(
              modifier = Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)),
              trackColor = colorScheme.surfaceVariant,
              color = colorScheme.primary.copy(alpha = 0.6f),
              strokeCap = StrokeCap.Round,
          )
        }
      } else {
        // [Paused 试听中]
        Row(
            modifier = Modifier.fillMaxWidth().height(36.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          // 复用 CircularIconButton 作为播放/暂停
          CircularIconButton(
              onClick = { onAction(VoiceNoteAction.TogglePlayPreview) },
          ) {
            Icon(
                imageVector =
                    if (isPlayingPreview)
                        ImageVector.vectorResource(R.drawable.icon_pause_rounded_fill_48)
                    else Icons.Rounded.PlayArrow,
                contentDescription = "Play or Pause",
                tint = colorScheme.onSurface,
                modifier = Modifier.size(ActionIconSize),
            )
          }

          VoiceNoteProgressSlider(
              progress = previewProgress,
              onSeek = { onAction(VoiceNoteAction.SeekPreview(it)) },
              modifier = Modifier.weight(1f),
          )

          PlayerDurationText(
              text = formattedDuration,
              style = durationTextStyle,
          )
        }
      }
    }

    // ---------------- 底部：删除 + 核心操作 (Pause/Continue) + 发送 ----------------
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      // 1. 复用 CircularIconButton：删除按钮
      CircularIconButton(
          onClick = { onAction(VoiceNoteAction.Delete) },
          backgroundColor = colorScheme.errorContainer,
      ) {
        Icon(
            painter = painterResource(R.drawable.icon_delete_rounded_24_scale_70pct),
            contentDescription = stringResource(R.string.btn_delete),
            tint = colorScheme.error, // 或者使用 colorScheme.onErrorContainer
            modifier = Modifier.size(ActionIconSize),
        )
      }

      VoiceNoteCenterActionButton(
          isPaused = isPaused,
          onPauseClick = { onAction(VoiceNoteAction.PauseRecording) },
          onResumeClick = { onAction(VoiceNoteAction.ResumeRecording) },
      )

      // 3. 复用 CircularIconButton：发送按钮（Primary 绿色背景，白/深字）
      CircularIconButton(
          onClick = { onAction(VoiceNoteAction.Send) },
          backgroundColor = colorScheme.primary,
      ) {
        Icon(
            painter = painterResource(R.drawable.icon_arrow_upward_alt_24px),
            contentDescription = stringResource(R.string.composer_send_btn_desc),
            tint = colorScheme.onPrimary,
            modifier = Modifier.size(ActionIconSize),
        )
      }
    }
  }
}

@Composable
fun VoiceNoteCenterActionButton(
    isPaused: Boolean,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme

  // 1. 背景色流转：
  // 录音中：surfaceContainerHigh (干净的浅灰偏白，无视觉噪音)
  // 暂停后：secondaryContainer (温和透气的淡薄荷绿，比 primary 浅很多)
  val buttonBgColor by
      animateColorAsState(
          targetValue =
              if (isPaused) colorScheme.secondaryContainer else colorScheme.surfaceContainerHigh,
          animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
          label = "VoiceNoteButtonBgColor",
      )

  // 2. 文字/图标颜色流转：
  // 录音中：onSurface (纯正深黑/极高对比，杜绝禁用感)
  // 暂停后：onSecondaryContainer (高质感深墨绿，层次分明)
  val buttonContentColor by
      animateColorAsState(
          targetValue = if (isPaused) colorScheme.onSecondaryContainer else colorScheme.onSurface,
          animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
          label = "VoiceNoteButtonContentColor",
      )

  // 3. 边框：仅在录音中加一条淡淡的 outlineVariant，赋予浅灰背景利落的实体感与可点击暗示
  val borderStroke =
      if (!isPaused) {
        BorderStroke(0.8.dp, colorScheme.outlineVariant.copy(alpha = 0.6f))
      } else {
        null
      }

  FilledTonalButton(
      onClick = {
        if (isPaused) onResumeClick() else onPauseClick()
      },
      modifier =
          modifier
              .height(ActionButtonSize)
              .widthIn(min = 146.dp), // 保证 Pause / Continue 居中不跳跃，且留有多语言弹性
      shape = RoundedCornerShape(8.dp),
      border = borderStroke,
      colors =
          ButtonDefaults.filledTonalButtonColors(
              containerColor = buttonBgColor,
              contentColor = buttonContentColor,
          ),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
  ) {
    AnimatedContent(
        targetState = isPaused,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "VoiceNoteButtonContentTransition",
    ) { paused ->
      val pausedButtonPainter =
          if (paused) painterResource(R.drawable.icon_mic_rounded_24)
          else painterResource(R.drawable.icon_pause_rounded_nonfill_24)
      val pausedButtonDescription =
          if (paused) stringResource(R.string.composer_voice_note_btn_resume)
          else stringResource(R.string.composer_voice_note_btn_pause)
      Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Icon(
            painter = pausedButtonPainter,
            contentDescription = pausedButtonDescription,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = pausedButtonDescription,
            maxLines = 1,
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoiceNoteProgressSlider(
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme

  Slider(
      value = progress.coerceIn(0f, 1f),
      onValueChange = onSeek,
      modifier = modifier,
      // 滑块 Thumb：使用你主题自带的绿色系 Primary
      thumb = {
        Box(
            modifier =
                Modifier.size(width = 12.dp, height = 20.dp)
                    .clip(CircleShape)
                    .background(colorScheme.primary)
        )
      },
      // 轨道 Track：使用你主题自带的 SurfaceVariant
      track = { sliderState ->
        SliderDefaults.Track(
            sliderState = sliderState,
            trackInsideCornerSize = 5.dp,
            modifier = Modifier.height(10.dp),
            colors =
                SliderDefaults.colors(
                    activeTrackColor = colorScheme.surfaceVariant,
                    inactiveTrackColor = colorScheme.surfaceVariant,
                ),
            drawStopIndicator = null,
        )
      },
  )
}

@Preview(name = "VoiceNote - Light", showBackground = true)
@Preview(
    name = "VoiceNote - Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun VoiceNoteBarPreview() {
  ImlogTheme {
    Surface {
      Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        VoiceNoteBar(
            isPaused = false,
            formattedDuration = "00:15",
            isPlayingPreview = false,
            previewProgress = 0f,
            onAction = {},
        )
        VoiceNoteBar(
            isPaused = true,
            formattedDuration = "00:15",
            isPlayingPreview = false,
            previewProgress = 0.35f,
            onAction = {},
        )
      }
    }
  }
}
