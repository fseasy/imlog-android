package top.fseasy.imlog.features.home.topiclog.composer2.tabbedcard

import android.content.res.Configuration
import android.graphics.BlurMaskFilter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativePaint
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.fseasy.imlog.features.home.topiclog.composer2.ComposerAction
import top.fseasy.imlog.features.home.topiclog.composer2.ComposerDefaultStageAction
import top.fseasy.imlog.features.home.topiclog.composer2.ComposerInputModeContextActionUiState
import top.fseasy.imlog.features.home.topiclog.composer2.ComposerStage
import top.fseasy.imlog.features.home.topiclog.composer2.ComposerVoiceRecordingAction
import top.fseasy.imlog.ui.theme.ImlogTheme




fun Modifier.ambientShadow(
    shape: Shape,
    shadowColor: Color = Color.Black.copy(alpha = 0.16f),
    blurRadius: Dp = 8.dp,
) =
    this.drawBehind {
      drawIntoCanvas { canvas ->
        val paint =
            Paint().apply {
              color = shadowColor
              nativePaint.maskFilter =
                  BlurMaskFilter(
                      blurRadius.toPx(),
                      BlurMaskFilter.Blur.NORMAL,
                  )
            }
        val outline = shape.createOutline(size, layoutDirection, this)
        canvas.drawOutline(outline, paint)
      }
    }

@Composable
fun MessageComposerCard(
  stage: ComposerStage,
  redDotStages: Set<ComposerStage>,
  textFieldState: TextFieldState,
  sendEnabled: Boolean,
  composerContextActionState: ComposerInputModeContextActionUiState,
  onAction: (ComposerAction) -> Unit,
  modifier: Modifier = Modifier,
) {

  // 1. 最外层遮罩底板：紧贴屏幕边缘，屏蔽背景 Timeline 内容渗透，并拦截触摸防止点击穿透
  Surface(
      modifier = modifier,
      color = Color.Transparent, // 与页面主底色保持一致，形成天然遮罩
  ) {
    // 2. 内部悬浮输入卡片：保留优雅的圆角和外边距
    val shape = RoundedCornerShape(14.dp)
    Surface(
        modifier =
            Modifier.fillMaxWidth()
                .ambientShadow(RoundedCornerShape(14.dp))
                .padding(horizontal = 10.dp, vertical = 10.dp),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        //        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        //        shadowElevation = 1.dp,
    ) {
      //      HorizontalDivider(
      //          thickness = DividerDefaults.Thickness,
      //          color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
      //      )
      Column(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
          verticalArrangement = Arrangement.SpaceBetween,
      ) {
        // 上层舞台区 (Stage Area)
        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp, max = 80.dp)) {
          when (stage) {
            ComposerStage.Default -> {
              ComposerDefaultStage(
                  textFieldState = textFieldState,
                  onAction = onAction,
              )
            }
            ComposerStage.Recording -> {
              ComposerRecordingStage(
                  onAction = onAction,
              )
            }
            ComposerStage.Attachment -> {
              ComposerAttachmentStage(onAction = onAction)
            }
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 下层三段式底栏
        Composer2BottomBar(
            stage = stage,
            stageRedDots = redDotStages,
            sendEnabled = sendEnabled,
            contextActionState = composerContextActionState,
            onSelectStage = { newStage -> onAction(ComposerAction.SelectComposerStage(newStage)) },
            onSend = {
              when (stage) {
                ComposerStage.Default -> onAction(ComposerDefaultStageAction.SendText)
                ComposerStage.Recording -> onAction(ComposerVoiceRecordingAction.SendVoiceRecording)

                ComposerStage.Attachment -> Unit // Impossible
              }
            },
            onContextAction = onAction,
        )
      }
    }
  }
}

@Preview(name = "ComposerRecordingStage - Light", showBackground = true)
@Preview(
    name = "ComposerRecordingStage - Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ComposerCardPreview() {
  ImlogTheme {
    Surface {
      val textFieldState by remember { mutableStateOf(TextFieldState()) }
      Column {
        ComposerStage.entries.forEach { stage ->
          MessageComposerCard(
              stage = stage,
              redDotStages = emptySet(),
              textFieldState = textFieldState,
              sendEnabled = true,
              composerContextActionState =
                ComposerInputModeContextActionUiState(compressAlbumEnabled = true),
              onAction = {},
          )
        }
      }
    }
  }
}
