package top.fseasy.imlog.features.home.topiclog.timeline.messagebubble

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.fseasy.imlog.domain.util.toAppMessageTimeFormat
import kotlin.time.Duration

/**
 * @param progressProvider Function that provides current progress in [0, 1]
 * @param amplitudes Normalized amplitude values in [0, 1]
 * @param stretchToFit If true, stretches waveform across full width; otherwise adapts between min
 *   and max count based on amplitude size
 * @param barMinHeightRatio Minimum height ratio for silent/zero amplitudes
 */
@Composable
fun WaveformSlider(
    progressProvider: () -> Float,
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
  val totalBarSpace = barWidthPx + barGapPx
  val inactiveColor = tintColor.copy(alpha = 0.3f)

  val currentOnSeek by rememberUpdatedState(onSeek)

  Canvas(
      modifier =
          modifier.pointerInput(stretchToFit, totalBarSpace) {
            awaitEachGesture {
              val down = awaitFirstDown(requireUnconsumed = false)
              val canvasWidth = size.width.toFloat()

              val maxCount = (canvasWidth / totalBarSpace).toInt().coerceAtLeast(1)
              val minCount = (maxCount / 2).coerceAtLeast(1)
              val targetCount =
                  if (stretchToFit) {
                    maxCount
                  } else {
                    if (amplitudes.isEmpty()) minCount
                    else amplitudes.size.coerceIn(minCount, maxCount)
                  }
              val effectiveWidth = targetCount * totalBarSpace

              // 点击即触发 Seek
              val initialProgress = (down.position.x / effectiveWidth).coerceIn(0f, 1f)
              currentOnSeek(initialProgress)
              down.consume()

              // 持续拖拽（Drag）实时 Seek
              while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break // 手指抬起结束

                val dragProgress = (change.position.x / effectiveWidth).coerceIn(0f, 1f)
                currentOnSeek(dragProgress)
                change.consume()
              }
            }
          }
  ) {
    val canvasWidth = size.width
    val canvasHeight = size.height

    val maxCount = (canvasWidth / totalBarSpace).toInt().coerceAtLeast(1)
    val minCount = (maxCount / 2).coerceAtLeast(1)
    val targetCount =
        if (stretchToFit) {
          maxCount
        } else {
          if (amplitudes.isEmpty()) minCount else amplitudes.size.coerceIn(minCount, maxCount)
        }

    val currentProgress = progressProvider()
    val ampSize = amplitudes.size

    for (index in 0 until targetCount) {
      val amp =
          if (ampSize == 0) {
            barMinHeightRatio
          } else {
            val dataIndex =
                ((index.toFloat() / targetCount) * ampSize).toInt().coerceIn(0, ampSize - 1)
            amplitudes[dataIndex]
          }

      val isPlayed = (index.toFloat() / targetCount) <= currentProgress
      val finalAmp = amp.coerceIn(barMinHeightRatio, 1f)
      val barHeight = canvasHeight * finalAmp
      val startY = (canvasHeight - barHeight) / 2f
      val endY = startY + barHeight
      val x = index * totalBarSpace + barWidthPx / 2f

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
  val progressProvider = {
    if (duration > Duration.ZERO) {
      (playPosition.inWholeMilliseconds.toFloat() / duration.inWholeMilliseconds.toFloat())
          .coerceIn(0f, 1f)
    } else {
      0f
    }
  }

  Column(
      modifier = modifier,
      verticalArrangement = Arrangement.Center,
  ) {
    // Waveform Visualizer & Seek Area
    WaveformSlider(
        progressProvider = progressProvider,
        amplitudes = amplitudes,
        tintColor = tintColor,
        onSeek = onSeek,
        modifier =
            Modifier.fillMaxWidth()
                .height(32.dp), // 32.dp is ideal for chat bubbles (48dp is often too tall)
    )

    Spacer(modifier = Modifier.height(2.dp))

    // Timestamp Row
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
      // Left: Current playback position (or "0:00" when active)
      Text(
          text = playPosition.toAppMessageTimeFormat(),
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
          color = tintColor.copy(alpha = 0.7f),
      )

      // Right: Total voice duration
      Text(
          text = duration.toAppMessageTimeFormat(),
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
          color = tintColor.copy(alpha = 0.7f),
      )
    }
  }
}
