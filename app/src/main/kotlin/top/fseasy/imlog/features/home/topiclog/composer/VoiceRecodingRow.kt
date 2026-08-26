package top.fseasy.imlog.features.home.topiclog.composer

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.fseasy.imlog.R
import top.fseasy.imlog.domain.util.toAppMessageTimeFormat

@Composable
fun VoiceRecodingRow(
    voiceRecordingUiStateHolder: State<VoiceRecordingUiState>,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
  VoiceRecordingContent(
      voiceRecordingUiState = voiceRecordingUiStateHolder.value,
      onCancel = onCancel,
      onSend = onSend,
      modifier = modifier,
  )
}

// WhatsApp Colors
private val WhatsAppRed = Color(0xFFEA4335)
private val WhatsAppGreen = Color(0xFF00A884)

@Composable
private fun VoiceRecordingContent(
    voiceRecordingUiState: VoiceRecordingUiState,
    onCancel: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    // Optional: Pass live amplitude list (0f..1f) if your recorder exposes it
    amplitudes: List<Float> = emptyList(),
) {
  // 1. Efficient Blinking Animation (no coroutine overhead)
  val infiniteTransition = rememberInfiniteTransition(label = "recording_pulse")
  val redDotAlpha by
      infiniteTransition.animateFloat(
          initialValue = 1f,
          targetValue = 0.2f,
          animationSpec =
              infiniteRepeatable(
                  animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                  repeatMode = RepeatMode.Reverse,
              ),
          label = "red_dot_alpha",
      )

  Row(
      modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    // Main Pill Container (Recording info + visualizer + delete)
    Surface(
        modifier = Modifier.weight(1f).height(48.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 2.dp,
    ) {
      Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        // Delete / Cancel Button
        IconButton(
            onClick = onCancel,
            modifier = Modifier.size(36.dp),
        ) {
          Icon(
              imageVector = Icons.Default.Delete,
              contentDescription = stringResource(R.string.composer_delete_voice_btn_desc),
              tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
              modifier = Modifier.size(22.dp),
          )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Pulsing Red Dot
        Box(modifier = Modifier.size(10.dp).alpha(redDotAlpha).background(WhatsAppRed, CircleShape))

        Spacer(modifier = Modifier.width(8.dp))

        // Elapsed Recording Time
        Text(
            text = voiceRecordingUiState.elapsed.toAppMessageTimeFormat(),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Sound Wave Amplitude Visualizer
        AudioWaveVisualizer(
            modifier = Modifier.weight(1f).height(24.dp),
            amplitudes = amplitudes,
        )
      }
    }

    // Send button
    IconButton(
        onClick = onSend,
        modifier = Modifier.size(48.dp).clip(CircleShape).background(WhatsAppGreen),
    ) {
      Icon(
          imageVector = Icons.AutoMirrored.Filled.Send,
          contentDescription = stringResource(R.string.composer_send_voice_btn_desc),
          tint = Color.White,
          modifier = Modifier.size(22.dp),
      )
    }
  }
}

@Composable
private fun AudioWaveVisualizer(
    amplitudes: List<Float>,
    modifier: Modifier = Modifier,
    waveColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
) {
  // Default placeholder wave bars if no real-time stream is provided
  val bars =
      amplitudes.takeLast(16).ifEmpty {
        listOf(0.2f, 0.5f, 0.8f, 0.4f, 0.9f, 0.6f, 0.3f, 0.7f, 0.5f, 0.8f, 0.4f)
      }

  Row(
      modifier = modifier,
      horizontalArrangement = Arrangement.spacedBy(2.5.dp, Alignment.End),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    bars.forEach { amplitude ->
      val normalizedHeight = (amplitude.coerceIn(0.1f, 1f) * 20).dp
      Box(
          modifier =
              Modifier.width(3.dp)
                  .height(normalizedHeight)
                  .clip(RoundedCornerShape(2.dp))
                  .background(waveColor)
      )
    }
  }
}
