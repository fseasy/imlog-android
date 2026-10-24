package top.fseasy.imlog.features.home.topiclog.composer2.tabbedcard

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import top.fseasy.imlog.R
import top.fseasy.imlog.features.home.topiclog.composer2.ComposerBottomContextAction
import top.fseasy.imlog.features.home.topiclog.composer2.ComposerInputModeContextActionUiState
import top.fseasy.imlog.features.home.topiclog.composer2.ComposerStage

@Composable
fun ComposerInputModeContextActionBar(
  stage: ComposerStage,
  uiState: ComposerInputModeContextActionUiState,
  onAction: (ComposerBottomContextAction) -> Unit,
) {
  when (stage) {
    ComposerStage.Attachment ->
        AttachmentContextAction(uiState.compressAlbumEnabled) {
          onAction(ComposerBottomContextAction.ToggleCompressAlbum)
        }

    // 后续扩展示例：语音/文本模式的其他按钮
    // MessageInputMode.Voice -> {
    //     ContextActionPill(label = "Whisper", selected = false, onClick = { ... })
    // }

    // 无动作时不占位
    else -> Unit
  }
}

@Composable
private fun AttachmentContextAction(
    compressAlbumEnabled: Boolean,
    onToggleCompressAlbum: () -> Unit,
) {
  FilterChip(
      selected = compressAlbumEnabled,
      onClick = onToggleCompressAlbum,
      label = {
        Text(
            text = stringResource(R.string.composer_context_action_compress_album),
            style = MaterialTheme.typography.labelSmall,
        )
      },
      shape = CircleShape, // 设定为圆角胶囊
      //      border = null,
  )
}
