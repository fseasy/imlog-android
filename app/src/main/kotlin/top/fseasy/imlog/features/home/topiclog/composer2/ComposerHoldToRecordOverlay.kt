package top.fseasy.imlog.features.home.topiclog.composer2

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HoldToSpeakOverlay(
    gestureTarget: HoldToTalkGestureTarget,
    durationText: String,
    modifier: Modifier = Modifier,
) {
  Box(
      modifier = modifier.fillMaxSize(),
      contentAlignment = Alignment.BottomCenter,
  ) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(260.dp),
        shape = RoundedCornerShape(topStart = 160.dp, topEnd = 160.dp),
        color = Color(0xFF2C3E50),
    ) {
      Box(modifier = Modifier.fillMaxSize()) {
        // 左侧 Cancel 区域 (手势滑入红变亮)
        Surface(
            shape = CircleShape,
            color =
                if (gestureTarget == HoldToTalkGestureTarget.Cancel) Color(0xFFEF4444)
                else Color(0xFF9CA3AF),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 24.dp).size(64.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Text("Cancel", color = Color.White, fontSize = 14.sp)
          }
        }

        // 中间时间与波形
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text(durationText, color = Color.White, fontSize = 20.sp)
          Spacer(modifier = Modifier.height(8.dp))
          Icon(
              Icons.Default.Edit,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(32.dp),
          )
        }

        // 右侧 Lock 区域 (滑入绿亮，并在松手或越过阈值后无缝升格为 P7 免提录音)
        Surface(
            shape = CircleShape,
            color =
                if (gestureTarget == HoldToTalkGestureTarget.Lock) Color(0xFF10B981)
                else Color(0xFF9CA3AF),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 24.dp).size(64.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Lock, contentDescription = "Lock", tint = Color.White)
          }
        }
      }
    }
  }
}
