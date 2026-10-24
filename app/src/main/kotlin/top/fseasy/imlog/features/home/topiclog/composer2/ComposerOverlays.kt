package top.fseasy.imlog.features.home.topiclog.composer2

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import top.fseasy.imlog.R

@Composable
fun CameraOverlay(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Box(modifier = modifier.background(Color(0xFF808080)).systemBarsPadding()) {
    IconButton(
        onClick = onClose,
        modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
    ) {
      Icon(
          Icons.Rounded.Close,
          contentDescription = "Close",
          tint = Color.White,
          modifier = Modifier.size(36.dp),
      )
    }

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp, start = 32.dp, end = 32.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Spacer(modifier = Modifier.size(48.dp))
      Box(
          modifier = Modifier.size(76.dp).clip(CircleShape).clickable { /* 拍照逻辑 */ },
          contentAlignment = Alignment.Center,
      ) {
        Icon(
            painterResource(R.drawable.icon_radio_button_checked_48px),
            contentDescription = "Shutter",
            tint = Color.White,
            modifier = Modifier.fillMaxSize(),
        )
        Box(modifier = Modifier.size(20.dp).background(Color.White, CircleShape))
      }
      IconButton(onClick = { /* 翻转镜头 */ }, modifier = Modifier.size(48.dp)) {
        Icon(
            painterResource(R.drawable.flip_camera_android_48px),
            contentDescription = "Flip",
            tint = Color.White,
            modifier = Modifier.size(36.dp),
        )
      }
    }
  }
}
