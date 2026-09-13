package top.fseasy.imlog.features.home.topiclog.timeline.messagebubble

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.fseasy.imlog.R
import top.fseasy.imlog.features.home.topiclog.timeline.MessageContentUiModel

@Composable
fun GenericFileMessageBubble(
    content: MessageContentUiModel.GenericFile,
    modifier: Modifier = Modifier,
) {
  Row(
      modifier = modifier.widthIn(min = 220.dp, max = 260.dp).padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Box(
        modifier =
            Modifier.size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(LocalContentColor.current.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
      Icon(
          painter = painterResource(content.iconRes),
          contentDescription = stringResource(R.string.term_file),
          modifier = Modifier.size(24.dp),
      )
    }

    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
          text = content.displayFilename,
          style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
      )

      Text(
          text = content.formatedFileSize,
          style = MaterialTheme.typography.labelSmall,
          color = LocalContentColor.current.copy(alpha = 0.7f),
      )
    }
  }
}
