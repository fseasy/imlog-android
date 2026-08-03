package top.fseasy.imlog.features.home.createtopic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import top.fseasy.imlog.R
import top.fseasy.imlog.domain.model.TopicPresetAvatar
import top.fseasy.imlog.ui.model.AvatarUiModel
import top.fseasy.imlog.ui.model.TopicAvatarUiModel
import top.fseasy.imlog.ui.model.getAll
import top.fseasy.imlog.ui.model.toCoilModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TopicAvatarPickerBottomSheet(
    currentAvatar: TopicAvatarUiModel,
    onDismissRequest: () -> Unit,
    onSelectPreset: (AvatarUiModel.Preset<TopicPresetAvatar>) -> Unit,
    onPickFromGallery: () -> Unit,
) {
  val presets = remember { AvatarUiModel.Preset.getAll<TopicPresetAvatar>() }
  val currentPresetResId = (currentAvatar as? AvatarUiModel.Preset)?.resId
  val isCustomSelected = currentPresetResId == null

  ModalBottomSheet(onDismissRequest = onDismissRequest) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
      Text(
          text = stringResource(R.string.term_select_topic_avatar),
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.padding(bottom = 16.dp),
      )

      FlowRow(
          horizontalArrangement = Arrangement.spacedBy(16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
          modifier = Modifier.padding(vertical = 12.dp),
      ) {
        // Preset avatars
        presets.forEach { preset ->
          val isSelected = preset.resId == currentPresetResId
          AvatarItem(
              isSelected = isSelected,
              onClick = {
                onSelectPreset(preset)
                onDismissRequest()
              },
              enabled = !isSelected,
          ) {
            AsyncImage(
                model = preset.resId,
                contentDescription =
                    "${stringResource(R.string.term_avatar)} ${preset.backMapValue.name}",
            )
          }
        }

        if (isCustomSelected) {
          AvatarItem(
              isSelected = true,
              onClick = {},
              enabled = false,
          ) {
            AsyncImage(
                model = currentAvatar.toCoilModel(),
                contentDescription = stringResource(R.string.term_custom_topic_avatar),
            )
          }
        }

        // select from album
        AvatarItem(
            isSelected = false,
            onClick = {
              onPickFromGallery()
              onDismissRequest()
            },
        ) {
          Box(
              modifier =
                  Modifier.fillMaxSize()
                      .background(
                          color = MaterialTheme.colorScheme.surfaceVariant,
                          shape = CircleShape,
                      ),
          ) {
            Icon(
                painter = painterResource(R.drawable.icon_image),
                contentDescription = stringResource(R.string.term_select_from_photo_library),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp).align(Alignment.Center),
            )

            // + icon at the bottom end
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier.size(12.dp)
                        .align(Alignment.BottomEnd)
                        .offset(
                            x = 4.dp,
                            y = 4.dp,
                        ),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun AvatarItem(
    isSelected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
  Box(
      modifier =
          Modifier.size(56.dp)
              .clip(CircleShape)
              .then(
                  if (isSelected) {
                    Modifier.border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                    )
                  } else {
                    Modifier
                  },
              )
              .clickable(enabled = enabled, onClick = onClick),
      contentAlignment = Alignment.Center,
  ) {
    content()
  }
}
