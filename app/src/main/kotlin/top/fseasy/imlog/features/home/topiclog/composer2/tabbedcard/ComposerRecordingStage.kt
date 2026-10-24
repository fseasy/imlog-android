package top.fseasy.imlog.features.home.topiclog.composer2.tabbedcard

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.fseasy.imlog.R
import top.fseasy.imlog.features.home.topiclog.composer2.ComposerVoiceRecordingAction
import top.fseasy.imlog.features.home.topiclog.composer2.HoldToTalkGestureTarget
import top.fseasy.imlog.features.home.topiclog.composer2.TapRecordingState
import top.fseasy.imlog.ui.theme.ImlogTheme

@Composable
fun BoxScope.ComposerRecordingStage(
  onAction: (ComposerVoiceRecordingAction) -> Unit,
  modifier: Modifier = Modifier,
) {
  //  Row(
  //      modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
  //      // 2 个选项时：居中并保持合适间距 (60.dp)，避免 SpaceEvenly 导致分布过散
  //      horizontalArrangement = Arrangement.spacedBy(60.dp, Alignment.CenterHorizontally),
  //      verticalAlignment = Alignment.CenterVertically,
  //  ) {
  //    ComposerActionTile(
  //        icon = ImageVector.vectorResource(R.drawable.icon_mic),
  //        labelResId = R.string.composer_recording_stage_label_audio,
  //        onClick = {},
  //    )
  //    ComposerActionTile(
  //        icon = ImageVector.vectorResource(R.drawable.icon_camera_48px),
  //        labelResId = R.string.composer_recording_stage_label_camera,
  //        onClick = {},
  //    )
  //  }
  Row(
      modifier = modifier.matchParentSize(),
      horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    SolidActionCard(
        icon = ImageVector.vectorResource(R.drawable.icon_mic),
        label = "Recorder",
        onClick = {},
    )
    SolidActionCard(
        icon = ImageVector.vectorResource(R.drawable.icon_camera_48px),
        label = "Camera",
        onClick = {},
    )
  }
}

@Composable
fun SolidActionCard(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
      modifier =
          Modifier.clip(RoundedCornerShape(16.dp)) // 16dp 圆角，比全胶囊优雅很多
              // 采用和左下角选中态一致的高亮表面色（白天是纯白，暗黑模式是亮灰）
              .clickable(onClick = onClick)
              .padding(horizontal = 16.dp),
  ) {
    Icon(
        imageVector = icon,
        contentDescription = label,
        modifier = Modifier.size(48.dp), // 稍大一点的图标
        tint = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(modifier = Modifier.width(8.dp))
    Text(
        text = label,
        // 稍粗一点的字重，提升精致度和辨识度
        style =
            MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Medium
            ),
        color = MaterialTheme.colorScheme.onSurface,
    )
  }
}

@Preview(name = "ComposerRecordingStage - Light", showBackground = true)
@Preview(
    name = "ComposerRecordingStage - Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ComposerRecordingStagePreview() {
  ImlogTheme {
    Surface {
      Box(modifier = Modifier.size(368.dp, 60.dp)) {
        ComposerRecordingStage(
            onAction = {},
        )
      }
    }
  }
}

@Composable
fun MicStage(
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
    //    // 2. 长按模式舞台 (P3)
    //    subMode == VoiceSubMode.HoldToSpeak -> {
    //      Box(modifier = Modifier.fillMaxWidth().height(68.dp), contentAlignment =
    // Alignment.Center) {
    //        Row(
    //            verticalAlignment = Alignment.CenterVertically,
    //            modifier =
    //                Modifier.pointerInput(Unit) {
    //                      detectDragGesturesAfterLongPress(
    //                          onDragStart = { onHoldStart() },
    //                          onDragEnd = { onHoldEnd(true) },
    //                          onDragCancel = { onHoldEnd(false) },
    //                          onDrag = { change, dragAmount ->
    //                            change.consume()
    //                            when {
    //                              dragAmount.x < -40 ->
    // onGestureChange(HoldToRecordGestureTarget.Cancel)
    //                              dragAmount.x > 40 ->
    // onGestureChange(HoldToRecordGestureTarget.Lock)
    //                              else -> onGestureChange(HoldToRecordGestureTarget.Normal)
    //                            }
    //                          },
    //                      )
    //                    }
    //                    .padding(12.dp),
    //        ) {
    //          Icon(Icons.Default.Edit, contentDescription = null, tint = Color.Black)
    //          Spacer(modifier = Modifier.width(8.dp))
    //          Text("Hold To Speak", fontSize = 18.sp, color = Color.Black)
    //        }
    //      }
    //    }
    // 3. 点按录音空闲态 (P4)
    else -> {
      Box(
          modifier = Modifier.fillMaxWidth().height(68.dp),
          contentAlignment = Alignment.Center,
      ) {
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
          topLeft = Offset(startX, startY),
          size = Size(barWidth, barHeight),
          cornerRadius = CornerRadius(4f, 4f),
      )
    }
  }
}
