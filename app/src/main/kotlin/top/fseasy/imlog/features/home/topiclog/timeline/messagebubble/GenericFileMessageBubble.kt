package top.fseasy.imlog.features.home.topiclog.timeline.messagebubble

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.fseasy.imlog.features.home.topiclog.timeline.MessageContentUiModel

@Composable
fun FileMessageBubble(
    content: MessageContentUiModel.GenericFile,
) {
  Row(
      modifier = Modifier.padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Column {
      Text(
          text = content.originalFilename,
          style = MaterialTheme.typography.bodyMedium,
      )
      Text(
        text = "${content.formatedFileSize / 1024} KB",
        style = MaterialTheme.typography.labelSmall,
      )
    }
  }
}
