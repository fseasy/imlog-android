package top.fseasy.imlog.features.home.topiclog.composer2.classic

import android.graphics.BlurMaskFilter
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativePaint
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.dropShadow(
    shape: Shape,
    color: Color = Color.Black.copy(alpha = 0.08f), // 调淡，太浓会假
    blur: Dp = 12.dp,
    offsetY: Dp = 3.dp,
    offsetX: Dp = 0.dp,
) =
    this.drawBehind {
      val radiusPx = blur.toPx()
      if (radiusPx <= 0f) return@drawBehind

      drawIntoCanvas { canvas ->
        val paint =
            Paint().apply {
              this.color = color
              isAntiAlias = true
              nativePaint.isAntiAlias = true
              // 使用 NORMAL：中心被 Surface 遮住，外圈柔焦漫反射
              nativePaint.maskFilter = BlurMaskFilter(radiusPx, BlurMaskFilter.Blur.NORMAL)
            }

        canvas.save()
        // 偏移量
        canvas.translate(offsetX.toPx(), offsetY.toPx())

        // 关键点：将 Outline 转化为 Path 绘制
        // Android 硬件加速只对 Path 执行 BlurMaskFilter，直接画 RoundRect 会被显卡吞掉滤镜
        val path = Path()
        when (val outline = shape.createOutline(size, layoutDirection, this)) {
          is Outline.Rectangle -> path.addRect(outline.rect)
          is Outline.Rounded -> path.addRoundRect(outline.roundRect)
          is Outline.Generic -> path.addPath(outline.path)
        }

        canvas.drawPath(path, paint)
        canvas.restore()
      }
    }

@Composable
internal fun DockFloatingContent(
    mode: ClassicComposerMode,
    textFieldState: TextFieldState,
    onAttachmentClick: () -> Unit,
    onSendText: () -> Unit,
    onTextFieldFocusChanged: (focused: Boolean) -> Unit,
    onHoldToRecordAction: (ComposerHoldToRecordAction) -> Unit,
    modifier: Modifier = Modifier,
) {
  val shape = RoundedCornerShape(20.dp) // 建议 16~20dp，胶囊圆角更美观

  Surface(
      modifier =
          modifier
              .fillMaxWidth()
              // 1. padding 给阴影扩散留出空间，防止贴近屏幕边缘被裁剪
              .padding(horizontal = 12.dp, vertical = 8.dp)
              // 2. 挂载全版本兼容的阴影
              .dropShadow(
                  shape = shape,
                  blur = 10.dp,
                  offsetY = 3.dp,
                  color = Color.Black.copy(alpha = 0.06f),
              ),
      shape = shape,
      color = MaterialTheme.colorScheme.surface,
      border =
          BorderStroke(
              width = 0.8.dp,
              color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
          ),
  ) {
    AnimatedContent(
        targetState = mode,
        label = "ComposerDockFloatingContentTransition",
        contentKey = { m ->
          when (m) {
            ClassicComposerMode.VoiceNote -> "VoiceNote"
            else -> "ComposerBar"
          }
        },
    ) { currentMode ->
      when (currentMode) {
        ClassicComposerMode.VoiceNote -> {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Placeholder for VoiceNote")
          }
        }
        else -> {
          ComposerBar(
              textFieldState = textFieldState,
              onAttachmentClick = onAttachmentClick,
              onTextFieldFocusChanged = onTextFieldFocusChanged,
              onSendText = onSendText,
              onHoldToRecordAction = onHoldToRecordAction,
          )
        }
      }
    }
  }
}
