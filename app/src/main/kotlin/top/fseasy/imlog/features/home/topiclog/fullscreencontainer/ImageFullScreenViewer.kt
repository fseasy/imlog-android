package top.fseasy.imlog.features.home.topiclog.fullscreencontainer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import top.fseasy.imlog.R

@Composable
fun ImageFullScreenViewer(
    imageUrl: Any,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    maxScale: Float = 4f,
    minScale: Float = 1f,
) {
  val coroutineScope = rememberCoroutineScope()

  /** Scale & Offset is done by handling modifier.graphicsLayer */
  // scale and offset for graphicsLayer
  var scale by remember { mutableFloatStateOf(1f) }
  var offset by remember { mutableStateOf(Offset.Zero) }
  var containerSize by remember { mutableStateOf(IntSize.Zero) }

  // animation for scale
  val scaleAnimatable = remember { Animatable(1f) }
  val offsetAnimatable = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

  fun clampOffset(targetOffset: Offset, currentScale: Float): Offset {
    if (currentScale <= 1f) return Offset.Zero
    val maxX = (containerSize.width * (currentScale - 1f)) / 2f
    val maxY = (containerSize.height * (currentScale - 1f)) / 2f
    return Offset(
        x = targetOffset.x.coerceIn(-maxX, maxX),
        y = targetOffset.y.coerceIn(-maxY, maxY),
    )
  }

  fun animateTo(targetScale: Float, targetOffset: Offset) {
    coroutineScope.launch {
      launch {
        scaleAnimatable.animateTo(targetScale, tween(300)) {
          scale = value
        }
      }
      launch {
        offsetAnimatable.animateTo(targetOffset, tween(300)) {
          offset = value
        }
      }
    }
  }

  Box(
      modifier =
          modifier
              .fillMaxSize()
              .background(Color.Black)
              .onSizeChanged { containerSize = it } // Get container size to calculate the pan edge
              .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                      onDismiss() // Dismiss when single tap
                    },
                    onDoubleTap = { tapOffset ->
                      val targetScale = if (scale > 1.2f) 1f else 3f
                      val targetOffset =
                          if (targetScale == 1f) {
                            Offset.Zero
                          } else {
                            // Zoom in from the tap point
                            val centerX = containerSize.width / 2f
                            val centerY = containerSize.height / 2f
                            val rawOffset =
                                Offset(
                                    x = (centerX - tapOffset.x) * (targetScale - 1f),
                                    y = (centerY - tapOffset.y) * (targetScale - 1f),
                                )
                            clampOffset(rawOffset, targetScale)
                          }

                      // prepare to animate
                      coroutineScope.launch {
                        scaleAnimatable.snapTo(scale)
                        offsetAnimatable.snapTo(offset)
                        animateTo(targetScale, targetOffset)
                      }
                    },
                )
              }
              // 2. pinch and pan
              .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                  val oldScale = scale
                  val newScale = (oldScale * zoom).coerceIn(minScale, maxScale)

                  if (newScale > 1f) {
                    val containerCenter =
                        Offset(containerSize.width / 2f, containerSize.height / 2f)
                    val targetOffset = offset + pan - (centroid - containerCenter) * (zoom - 1f)

                    scale = newScale
                    offset = clampOffset(targetOffset, newScale)
                  } else {
                    scale = newScale
                    offset = Offset.Zero
                  }
                }
              },
      contentAlignment = Alignment.Center,
  ) {
    AsyncImage(
        model = imageUrl,
        contentDescription = stringResource(R.string.term_full_screen_image),
        contentScale = ContentScale.Fit,
        modifier =
            Modifier.fillMaxSize()
                //  graphicsLayer is the efficient way that avoid Recomposition / Re-layout
                .graphicsLayer {
                  scaleX = scale
                  scaleY = scale
                  translationX = offset.x
                  translationY = offset.y
                },
    )
  }
}
