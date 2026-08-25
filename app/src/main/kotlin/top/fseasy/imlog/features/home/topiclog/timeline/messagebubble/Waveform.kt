package top.fseasy.imlog.features.home.topiclog.timeline.messagebubble

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.fseasy.imlog.domain.util.safeDivision
import top.fseasy.imlog.domain.util.toAppMessageTimeFormat
import kotlin.time.Duration

/**
 * @param progress Current progress in [0, 1]
 * @param amplitudes Normalized amplitude values in [0, 1]
 * @param stretchToFit If true, stretches waveform across full width; otherwise adapts between min
 *   and max count based on amplitude size
 * @param barMinHeightRatio Minimum height ratio for silent/zero amplitudes
 */
@Composable
fun WaveformSlider(
    progress: Float,
    amplitudes: List<Float>,
    tintColor: Color,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    stretchToFit: Boolean = false,
    barWidth: Dp = 3.dp,
    barGap: Dp = 2.dp,
    barMinHeightRatio: Float = 0.1f,
) {
  val density = LocalDensity.current
  val barWidthPx = with(density) { barWidth.toPx() }
  val barGapPx = with(density) { barGap.toPx() }
  val inactiveColor = tintColor.copy(alpha = 0.3f)

  var activeWaveformWidthPx by remember { mutableFloatStateOf(1f) }

  Canvas(
      modifier =
          modifier
              .pointerInput(stretchToFit) {
                detectTapGestures { offset ->
                  val effectiveWidth =
                      if (stretchToFit) size.width.toFloat() else activeWaveformWidthPx
                  val newProgress = (offset.x / effectiveWidth).coerceIn(0f, 1f)
                  onSeek(newProgress)
                }
              }
              .pointerInput(stretchToFit) {
                detectHorizontalDragGestures { change, _ ->
                  change.consume()
                  val effectiveWidth =
                      if (stretchToFit) size.width.toFloat() else activeWaveformWidthPx
                  val newProgress = (change.position.x / effectiveWidth).coerceIn(0f, 1f)
                  onSeek(newProgress)
                }
              }
  ) {
    val canvasWidth = size.width
    val canvasHeight = size.height

    val totalBarSpace = barWidthPx + barGapPx
    val maxCount = (canvasWidth / totalBarSpace).toInt().coerceAtLeast(1)
    val minCount = (maxCount / 2).coerceAtLeast(1)

    val targetCount =
        if (stretchToFit) {
          maxCount
        } else {
          if (amplitudes.isEmpty()) minCount else amplitudes.size.coerceIn(minCount, maxCount)
        }

    activeWaveformWidthPx = targetCount * totalBarSpace

    val sampledAmplitudes =
        if (amplitudes.isEmpty()) {
          List(targetCount) { barMinHeightRatio }
        } else {
          List(targetCount) { index ->
            val dataIndex =
                ((index.toFloat() / targetCount) * amplitudes.size)
                    .toInt()
                    .coerceIn(0, amplitudes.size - 1)
            amplitudes[dataIndex]
          }
        }

    sampledAmplitudes.forEachIndexed { index, amp ->
      val x = index * totalBarSpace + barWidthPx / 2
      val isPlayed = (index.toFloat() / targetCount) <= progress

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

/** Used for Audio/Voice bubble */
@Composable
fun WaveformWithProgressColumn(
    amplitudes: List<Float>,
    isActive: Boolean,
    duration: Duration,
    activePlayPositionHolder: State<Duration>,
    inactivePlayPosition: Duration,
    onSeek: (Float) -> Unit,
    tintColor: Color,
    modifier: Modifier = Modifier,
) {
  val playPosition = if (isActive) activePlayPositionHolder.value else inactivePlayPosition

  Column(modifier = modifier) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        val progress = playPosition.safeDivision(duration).coerceIn(0f, 1f)

        WaveformSlider(
            progress = progress,
            amplitudes = amplitudes,
            tintColor = tintColor,
            onSeek = onSeek,
            modifier = Modifier.fillMaxWidth().height(48.dp), // waveform height
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          if (playPosition > Duration.ZERO) {
            Text(
                text = playPosition.toAppMessageTimeFormat(),
                style = MaterialTheme.typography.labelSmall,
                color = tintColor.copy(alpha = 0.6f),
            )
          }

          Text(
              text = duration.toAppMessageTimeFormat(),
              style = MaterialTheme.typography.labelSmall,
              color = tintColor.copy(alpha = 0.6f),
          )
        }
      }
    }
  }
}
