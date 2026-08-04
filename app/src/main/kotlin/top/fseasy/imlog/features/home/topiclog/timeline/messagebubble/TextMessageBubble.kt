package top.fseasy.imlog.features.home.topiclog.timeline.messagebubble

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TextMessageBubble(
    text: String,
    isOwnMessage: Boolean,
) {
  Text(
      text = text,
      modifier = Modifier.padding(12.dp),
      style = MaterialTheme.typography.bodyMedium,
      color =
          if (isOwnMessage) MaterialTheme.colorScheme.onPrimary
          else MaterialTheme.colorScheme.onSurfaceVariant,
  )
}
