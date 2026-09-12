package top.fseasy.imlog.features.home.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import top.fseasy.imlog.R
import top.fseasy.imlog.ui.model.TopicAvatarUiModel
import top.fseasy.imlog.ui.model.toCoilModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeTopicListItem(
    topic: HomeTopicUiModel,
    isContextMenuVisible: Boolean,
    onDismissContextMenu: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPinClick: () -> Unit,
    onSettingClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Box(
      modifier =
          modifier
              .fillMaxWidth()
              .combinedClickable(
                  onClick = onClick,
                  onLongClick = onLongClick,
              )
              .padding(horizontal = 16.dp, vertical = 16.dp),
      contentAlignment = Alignment.CenterStart,
  ) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      TopicAvatar(
          name = topic.name,
          avatar = topic.avatarUiModel,
          size = 50.dp,
      )

      Spacer(modifier = Modifier.width(16.dp))

      Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.Center,
      ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
              text = topic.name,
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f, fill = false),
          )

          Spacer(modifier = Modifier.width(8.dp))

          Text(
              text = topic.messageFormatedUpdatedAt,
              style = MaterialTheme.typography.labelSmall,
              color =
                  if (topic.hasUnread) {
                    MaterialTheme.colorScheme.primary
                  } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                  },
          )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
              text = topic.messageSnippet.toAnnotatedString(),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f),
          )

          if (topic.isPinned || topic.hasUnread) {
            Spacer(modifier = Modifier.width(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              if (topic.isPinned) {
                Icon(
                    painter = painterResource(R.drawable.icon_keep),
                    contentDescription = stringResource(R.string.term_pinned),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
              }

              if (topic.hasUnread) {
                Box(
                    modifier =
                        Modifier.size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                )
              }
            }
          }
        }
      }
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
private fun TopicAvatar(
    name: String,
    avatar: TopicAvatarUiModel,
    modifier: Modifier = Modifier,
    size: Dp = 50.dp,
) {
  val contentDescription = stringResource(R.string.topic_avatar_content_description, name)

  AsyncImage(
      model = avatar.toCoilModel(),
      contentDescription = contentDescription,
      modifier =
          modifier
              .size(size)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)),
      contentScale = ContentScale.Crop,
  )
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
