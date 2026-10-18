package top.fseasy.imlog.features.home.topiclog.composer2

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.fseasy.imlog.R

@Composable
fun ComposerAttachmentStage(onSelect: (AttachmentType) -> Unit) {
  Row(
      modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically,
  ) {
    AttachmentTile(ImageVector.vectorResource(R.drawable.icon_album), "Album") {
      onSelect(AttachmentType.Album)
    }
    AttachmentTile(ImageVector.vectorResource(R.drawable.icon_audio_file), "Audio") {
      onSelect(AttachmentType.Audio)
    }
    AttachmentTile(ImageVector.vectorResource(R.drawable.icon_files), "File") {
      onSelect(AttachmentType.GenericFile)
    }
  }
}

@Composable
private fun AttachmentTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
  Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier =
          Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(10.dp),
  ) {
    Icon(icon, contentDescription = label, modifier = Modifier.size(34.dp), tint = Color.Black)
    Spacer(modifier = Modifier.height(4.dp))
    Text(label, fontSize = 13.sp, color = Color.Black)
  }
}
