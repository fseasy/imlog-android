package top.fseasy.imlog.features.home.topiclog.timeline.messagebubble

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.nio.file.Path

sealed interface PreviewMedia {
  data class Image(val path: Path) : PreviewMedia

  data class Video(val path: Path) : PreviewMedia
}

@Composable
fun FullScreenMediaPreviewDialog(
    media: PreviewMedia,
    onDismiss: () -> Unit,
) {
  // 使用全屏 Dialog
  Dialog(
      onDismissRequest = onDismiss,
      properties = DialogProperties(usePlatformDefaultWidth = false), // 突破默认边框，全屏显示
  ) {
    Box(
        modifier =
            Modifier.fillMaxSize().background(Color.Black).clickable {
              onDismiss()
            }, // 点击空白处/任意处退出全屏
        contentAlignment = Alignment.Center,
    ) {
      when (media) {
        is PreviewMedia.Image -> {
          AsyncImage(
              model = media.path.toFile(),
              contentDescription = "Full Screen Image",
              modifier = Modifier.fillMaxSize(),
          )
        }

        is PreviewMedia.Video -> {
          // 全屏视频播放器 (Media3 AndroidView / PlayerView)
          // 进入全屏后自动播放 (autoPlay = true)
          FullScreenVideoPlayer(
              videoPath = media.path,
              autoPlay = true,
          )
        }
      }
    }
  }
}

