package top.fseasy.imlog.features.home.createtopic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import top.fseasy.imlog.R
import top.fseasy.imlog.ui.model.TaskExecuteWithDefaultState
import top.fseasy.imlog.ui.model.TopicAvatarUiModel
import top.fseasy.imlog.ui.model.toCoilModel

@Composable
fun TopicAvatarBadgeView(
    avatarTask: TaskExecuteWithDefaultState<TopicAvatarUiModel>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val contentDescription =
      stringResource(
          R.string.topic_avatar_content_description,
          stringResource(R.string.term_new_topic),
      )

  Box(
      modifier = modifier.size(72.dp).clip(CircleShape).clickable(onClick = onClick),
      contentAlignment = Alignment.Center,
  ) {
    AsyncImage(
        model = avatarTask.data.toCoilModel(),
        contentDescription = contentDescription,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
    )
    // Attach an edit icon or Loading icon
    Box(
        modifier =
            Modifier.align(Alignment.BottomEnd)
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
    ) {
      val attachedModifier =
          Modifier.size(12.dp)
              .align(Alignment.BottomEnd)
              .offset(
                  x = 4.dp,
                  y = 4.dp,
              )
      if (avatarTask !is TaskExecuteWithDefaultState.Executing) {
        Icon(
            imageVector = Icons.Filled.Edit,
            contentDescription = stringResource(R.string.term_edit_topic_avatar),
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = attachedModifier,
        )
      } else {
        CircularProgressIndicator(attachedModifier)
      }
    }
  }
}
