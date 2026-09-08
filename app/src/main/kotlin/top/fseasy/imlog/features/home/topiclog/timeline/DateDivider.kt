package top.fseasy.imlog.features.home.topiclog.timeline

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DateDivider(
    text: String,
    modifier: Modifier = Modifier,
) {
  Box(
      modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
      contentAlignment = Alignment.Center,
  ) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(12.dp),
    ) {
      Text(
          text = text,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
      )
    }
  }
}
