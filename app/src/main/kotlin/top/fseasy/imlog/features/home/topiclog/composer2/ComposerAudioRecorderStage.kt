package top.fseasy.imlog.features.home.topiclog.composer2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.fseasy.imlog.R

@Composable
fun MicStage(
  subMode: VoiceSubMode,
  recordingState: TapRecordingState,
  onStartRecord: () -> Unit,
  onPause: () -> Unit,
  onResume: () -> Unit,
  onDiscard: () -> Unit,
  onTogglePlay: () -> Unit,
  onHoldStart: () -> Unit,
  onHoldEnd: (Boolean) -> Unit,
  onGestureChange: (HoldToTalkGestureTarget) -> Unit,
) {
  when {
    // 1. 免提录音中或暂停态：Row 1 状态 + Row 2 操作
    recordingState !is TapRecordingState.Idle -> {
      val durationSec =
          when (recordingState) {
            is TapRecordingState.Recording -> recordingState.durationSec
            is TapRecordingState.Paused -> recordingState.durationSec
            else -> 0
          }
      val isPaused = recordingState is TapRecordingState.Paused
      val isPlaying = (recordingState as? TapRecordingState.Paused)?.isPlayingPreview == true

      Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        // Row 1: 状态行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
              text = String.format("%d:%02d", durationSec / 60, durationSec % 60),
              fontSize = 17.sp,
              color = Color.Black,
          )
          WaveformView(modifier = Modifier.weight(1f).height(24.dp).padding(horizontal = 16.dp))
        }

        // Row 2: 操作行 (大热区药丸排布，防止误触)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          // 删除
          Surface(
              shape = RoundedCornerShape(12.dp),
              color = Color(0xFFD6D8DC),
              onClick = onDiscard,
              modifier = Modifier.weight(1f).height(42.dp),
          ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(Icons.Outlined.Delete, contentDescription = null, tint = Color.Black)
              Spacer(modifier = Modifier.width(6.dp))
              Text("Delete", color = Color.Black, fontSize = 14.sp)
            }
          }

          // 试听 (暂停态可见)
          if (isPaused) {
            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier.size(42.dp).clip(CircleShape).background(Color(0xFFD6D8DC)),
            ) {
              Icon(
                  imageVector =
                      if (isPlaying) ImageVector.vectorResource(R.drawable.icon_pause_rounded_fill)
                      else Icons.Rounded.PlayArrow,
                  contentDescription = "Preview",
                  tint = Color.Black,
              )
            }
          }

          // 暂停 / 继续
          Surface(
              shape = RoundedCornerShape(12.dp),
              color = Color(0xFFD6D8DC),
              onClick = { if (isPaused) onResume() else onPause() },
              modifier = Modifier.weight(1.5f).height(42.dp),
          ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                  imageVector =
                      if (isPaused)
                          ImageVector.vectorResource(R.drawable.icon_radio_button_checked_48px)
                      else ImageVector.vectorResource(R.drawable.icon_pause_rounded_fill),
                  contentDescription = null,
                  tint = Color.Black,
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(if (isPaused) "Continue" else "Pause", color = Color.Black, fontSize = 14.sp)
            }
          }
        }
      }
    }
    // 2. 长按模式舞台 (P3)
    subMode == VoiceSubMode.HoldToSpeak -> {
      Box(modifier = Modifier.fillMaxWidth().height(68.dp), contentAlignment = Alignment.Center) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier.pointerInput(Unit) {
                      detectDragGesturesAfterLongPress(
                          onDragStart = { onHoldStart() },
                          onDragEnd = { onHoldEnd(true) },
                          onDragCancel = { onHoldEnd(false) },
                          onDrag = { change, dragAmount ->
                            change.consume()
                            when {
                              dragAmount.x < -40 -> onGestureChange(HoldToTalkGestureTarget.Cancel)
                              dragAmount.x > 40 -> onGestureChange(HoldToTalkGestureTarget.Lock)
                              else -> onGestureChange(HoldToTalkGestureTarget.Normal)
                            }
                          },
                      )
                    }
                    .padding(12.dp),
        ) {
          Icon(Icons.Default.Edit, contentDescription = null, tint = Color.Black)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Hold To Speak", fontSize = 18.sp, color = Color.Black)
        }
      }
    }
    // 3. 点按录音空闲态 (P4)
    else -> {
      Box(modifier = Modifier.fillMaxWidth().height(68.dp), contentAlignment = Alignment.Center) {
        Button(
            onClick = onStartRecord,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        ) {
          Icon(
              painterResource(R.drawable.icon_radio_button_checked_48px),
              contentDescription = null,
              tint = Color.Black,
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text("Tap To Record", fontSize = 18.sp, color = Color.Black)
        }
      }
    }
  }
}

@Composable
fun WaveformView(modifier: Modifier = Modifier) {
  Canvas(modifier = modifier) {
    val barWidth = 3.dp.toPx()
    val barSpacing = 3.dp.toPx()
    val totalBars = (size.width / (barWidth + barSpacing)).toInt().coerceAtLeast(1)
    for (i in 0 until totalBars) {
      val barHeight = size.height * ((i % 5 + 2) / 7f)
      val startX = i * (barWidth + barSpacing)
      val startY = (size.height - barHeight) / 2
      drawRoundRect(
          color = Color.Black,
          topLeft = androidx.compose.ui.geometry.Offset(startX, startY),
          size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
          cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
      )
    }
  }
}
