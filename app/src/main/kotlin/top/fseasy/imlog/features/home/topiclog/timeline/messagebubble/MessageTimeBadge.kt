package top.fseasy.imlog.features.home.topiclog.timeline.messagebubble

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MessageTimeText(
    timeText: String,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current.copy(alpha = 0.75f),
) {
  Text(
      text = timeText,
      modifier = modifier.padding(horizontal = 8.dp, vertical = 2.dp),
      style = textStyle,
      color = color,
  )
}

@Composable
fun MessageTimeOverlayBadge(
    timeText: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.scrim,
    contentColor: Color = Color.White,
) {
  Box(
      modifier =
          modifier
              .clip(RoundedCornerShape(10.dp))
              .background(containerColor)
              .padding(horizontal = 6.dp, vertical = 2.dp)
  ) {
    Text(
        text = timeText,
        style = textStyle,
        color = contentColor,
    )
  }
}

private val textStyle: TextStyle
  @Composable get() = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
