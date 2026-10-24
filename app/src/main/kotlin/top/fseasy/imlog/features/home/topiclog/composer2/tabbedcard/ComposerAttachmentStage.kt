package top.fseasy.imlog.features.home.topiclog.composer2.tabbedcard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import top.fseasy.imlog.R
import top.fseasy.imlog.features.home.topiclog.composer2.ComposerAttachmentStageAction

@Composable
fun ComposerAttachmentStage(
  onAction: (ComposerAttachmentStageAction) -> Unit,
  modifier: Modifier = Modifier,
) {

  val albumPickerLauncher =
      rememberLauncherForActivityResult(
          // getPickImagesMaxLimit() Needs R Extensions Version 2. so just set a hard limit
          contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 9),
          onResult = { uris ->
            if (uris.isNotEmpty()) {
              onAction(ComposerAttachmentStageAction.SelectAlbums(uris))
            }
          },
      )
  val audioPickerLauncher =
      rememberLauncherForActivityResult(
          contract = ActivityResultContracts.OpenMultipleDocuments(),
          onResult = { uris ->
            if (uris.isNotEmpty()) {
              onAction(ComposerAttachmentStageAction.SelectAudios(uris))
            }
          },
      )
  val filePickerLauncher =
      rememberLauncherForActivityResult(
          contract = ActivityResultContracts.OpenMultipleDocuments(),
          onResult = { uris ->
            if (uris.isNotEmpty()) {
              onAction(ComposerAttachmentStageAction.SelectFiles(uris))
            }
          },
      )

  Row(
      modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically,
  ) {
    SolidActionCard(
        icon = ImageVector.vectorResource(R.drawable.icon_album),
        //        labelResId = R.string.composer_attachment_stage_label_album,
        label = "Album",
        onClick = {
          albumPickerLauncher.launch(PickVisualMediaRequest(PickVisualMedia.ImageAndVideo))
        },
    )
    //    ComposerActionTile(
    //        icon = ImageVector.vectorResource(R.drawable.icon_audio_file),
    //        labelResId = R.string.composer_attachment_stage_label_audio,
    //        onClick = { audioPickerLauncher.launch(arrayOf("audio/*")) },
    //    )
    SolidActionCard(
        icon = ImageVector.vectorResource(R.drawable.icon_files),
        //        labelResId = R.string.composer_attachment_stage_label_file,
        label = "File",
        onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
    )
  }
}
