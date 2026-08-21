package top.fseasy.imlog.features.home.topiclog.composer

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import top.fseasy.imlog.R
import top.fseasy.imlog.domain.util.toMmSsFormat
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun VoiceRecodingRow(
    voiceRecordingUiStateHolder: State<VoiceRecordingUiState>,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
  VoiceRecodingContent(
      voiceRecordingUiState = voiceRecordingUiStateHolder.value,
      onCancel = onCancel,
      onSend = onSend,
      modifier = modifier,
  )
}

@Composable
fun VoiceRecodingContent(
    voiceRecordingUiState: VoiceRecordingUiState,
    onCancel: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
  var blink by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    while (true) {
      blink = !blink
      delay(600.milliseconds)
    }
  }
  val alphaAnim by animateFloatAsState(targetValue = if (blink) 1f else 0.2f, label = "blink")

  Row(
      modifier =
          modifier
              .fillMaxWidth()
              .padding(horizontal = 12.dp, vertical = 8.dp)
              .height(38.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(Color(0xFFFFF0F0)),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    IconButton(onClick = onCancel) {
      Icon(
          imageVector = Icons.Default.Delete,
          contentDescription = stringResource(R.string.composer_delete_voice_btn_desc),
          tint = Color(0xFFFF3B30),
      )
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(
          modifier = Modifier.size(8.dp).alpha(alphaAnim).background(Color(0xFFFF3B30), CircleShape)
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
          text = (voiceRecordingUiState.elapsed.toMmSsFormat()),
          color = Color(0xFFFF3B30),
          fontSize = 14.sp,
          style = MaterialTheme.typography.bodyMedium,
      )
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(
          onClick = onSend,
          modifier =
              Modifier.size(32.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
      ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Send,
            contentDescription = stringResource(R.string.composer_send_voice_btn_desc),
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(16.dp),
        )
      }
      Spacer(modifier = Modifier.width(4.dp))
    }
  }
}
