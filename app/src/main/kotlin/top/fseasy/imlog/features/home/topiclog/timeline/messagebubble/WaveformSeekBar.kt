package top.fseasy.imlog.features.home.topiclog.timeline.messagebubble

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * @param progress [0, 1]
 * @param amplitudes normalized amplitudes, in [0, 1]
 * @param barWidth single bar width
 * @param barGap single bar gap
 * @param barMinHeightRatio Used to force display the line when amplitudes is zero
 */
@Composable
fun WaveformSlider(
    progress: Float,
    amplitudes: List<Float>,
    tintColor: Color,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    barWidth: Dp = 3.dp,
    barGap: Dp = 2.dp,
    barMinHeightRatio: Float = 0.1f, // 最小高度比例，防止振幅为0时看不到线
) {
  val density = LocalDensity.current
  val barWidthPx = with(density) { barWidth.toPx() }
  val barGapPx = with(density) { barGap.toPx() }

  val inactiveColor = tintColor.copy(alpha = 0.3f)

  Canvas(
      modifier =
          modifier
              .pointerInput(Unit) {
                detectTapGestures { offset ->
                  val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                  onSeek(newProgress)
                }
              }
              .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                  change.consume()
                  val newProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                  onSeek(newProgress)
                }
              }
  ) {
    val canvasWidth = size.width
    val canvasHeight = size.height

    val totalBarSpace = barWidthPx + barGapPx
    val count = (canvasWidth / totalBarSpace).toInt().coerceAtLeast(1)
    val minCount = count / 2

    val sampledAmplitudes =
        if (amplitudes.isEmpty()) {
          List(minCount) { barMinHeightRatio }
        } else {
          val displayCont = amplitudes.size.coerceIn(minCount, count)
          List(displayCont) { index ->
            val dataIndex =
                ((index.toFloat() / displayCont) * amplitudes.size)
                    .toInt()
                    .coerceIn(0, amplitudes.size - 1)
            amplitudes[dataIndex]
          }
        }

    sampledAmplitudes.forEachIndexed { index, amp ->
      val x = index * totalBarSpace + barWidthPx / 2

      val currentRatio = index.toFloat() / count
      val isPlayed = currentRatio <= progress

      val finalAmp = amp.coerceIn(barMinHeightRatio, 1f)
      val barHeight = canvasHeight * finalAmp
      val startY = (canvasHeight - barHeight) / 2
      val endY = startY + barHeight

      drawLine(
          color = if (isPlayed) tintColor else inactiveColor,
          start = Offset(x, startY),
          end = Offset(x, endY),
          strokeWidth = barWidthPx,
          cap = StrokeCap.Round,
      )
    }
  }
}
