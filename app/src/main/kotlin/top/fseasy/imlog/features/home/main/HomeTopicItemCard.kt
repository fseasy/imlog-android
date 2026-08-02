package top.fseasy.imlog.features.home.main

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import top.fseasy.imlog.R
import top.fseasy.imlog.ui.model.TopicAvatarUiModel
import top.fseasy.imlog.ui.model.toCoilModel

@Composable
fun HomeTopicItemCard(
    topic: HomeTopicUiModel,
    isContextMenuVisible: Boolean,
    onDismissContextMenu: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPinClick: () -> Unit,
    onSettingClick: () -> Unit,
    modifier: Modifier = Modifier,
) {

  val backgroundColor =
      if (topic.isPinned) {
        MaterialTheme.colorScheme.surfaceContainerHigh
      } else {
        MaterialTheme.colorScheme.surface
      }

  Box(
      modifier =
          modifier
              .fillMaxWidth()
              .background(backgroundColor)
              .combinedClickable(
                  onClick = onClick,
                  onLongClick = onLongClick,
              )
              .padding(horizontal = 16.dp, vertical = 12.dp)
  ) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      CardLeft(
          name = topic.name,
          avatar = topic.avatarUiModel,
          hasUnread = topic.hasUnread,
          modifier = Modifier.padding(end = 12.dp),
          avatarSize = 48.dp,
      )

      CardRight(
          name = topic.name,
          messageUpdatedAt = topic.messageUpdatedAt,
          messageSnippet = topic.messageSnippet,
          modifier = Modifier.weight(1f),
      )
    }

    TopicDropdownMenuContent(
        topic = topic,
        isContextMenuVisible = isContextMenuVisible,
        onDismissContextMenu = onDismissContextMenu,
        onPinClick = onPinClick,
        onSettingClick = onSettingClick,
    )
  }
}

@Composable
private fun CardLeft(
    name: String,
    avatar: TopicAvatarUiModel,
    hasUnread: Boolean,
    modifier: Modifier = Modifier,
    avatarSize: Dp = 48.dp,
) {
  val contentDescription = stringResource(R.string.topic_avatar_content_description, name)

  Box(
      contentAlignment = Alignment.TopEnd,
      modifier = modifier,
  ) {
    AsyncImage(
        model = avatar.toCoilModel(),
        contentDescription = contentDescription,
        modifier =
            Modifier.size(avatarSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        contentScale = ContentScale.Crop,
    )

    // 3. 未读红点
    if (hasUnread) {
      Box(
          modifier =
              Modifier.size(10.dp)
                  .offset(x = 2.dp, y = (-2).dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.error)
      )
    }
  }
}

@Composable
private fun CardRight(
    name: String,
    messageUpdatedAt: String,
    messageSnippet: MessageSnippet,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier = modifier,
      verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    // first row: Title + Time
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
          text = name,
          style = MaterialTheme.typography.titleMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.weight(1f, fill = false),
      )

      Spacer(modifier = Modifier.width(8.dp))

      Text(
          text = messageUpdatedAt,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    // 2nd row: message snippet
    Text(
        text = messageSnippet.toAnnotatedString(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun MessageSnippet.toAnnotatedString(): AnnotatedString {
  val headerColor = MaterialTheme.colorScheme.error
  return buildAnnotatedString {
    if (header.isNotBlank()) {
      withStyle(style = SpanStyle(color = headerColor)) {
        append(header)
        append(" ")
      }
    }
    append(content)
  }
}

/** menu is short, so choose the Dropdown menu in each TopicCard. */
@Composable
private fun TopicDropdownMenuContent(
    isContextMenuVisible: Boolean,
    onDismissContextMenu: () -> Unit,
    topic: HomeTopicUiModel,
    onPinClick: () -> Unit,
    onSettingClick: () -> Unit,
) {
  val pinButtonRes =
      if (topic.isPinned) R.string.topic_settings_btn_unpin else R.string.topic_settings_btn_pin
  DropdownMenu(
      expanded = isContextMenuVisible,
      onDismissRequest = onDismissContextMenu,
  ) {
    DropdownMenuItem(
        text = { Text(stringResource(pinButtonRes)) },
        onClick = onPinClick,
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.btn_setting)) },
        onClick = onSettingClick,
    )
  }
}
